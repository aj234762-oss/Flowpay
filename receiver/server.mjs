import { createServer } from "node:http";
import { createHmac, timingSafeEqual } from "node:crypto";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const PORT = Number(process.env.PORT || 8081);
const MAX_BODY_BYTES = 1024 * 1024;
const MAX_EVENTS = 200;
const pagePath = path.join(path.dirname(fileURLToPath(import.meta.url)), "index.html");
const page = await readFile(pagePath);
const deliveries = [];
let signingSecret = "";

function sendJson(response, status, value) {
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
  });
  response.end(JSON.stringify(value));
}

async function readBody(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) {
      const error = new Error("Request body is too large");
      error.status = 413;
      throw error;
    }
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

function signatureMatches(rawBody, providedSignature) {
  if (!signingSecret || !providedSignature) return false;
  const expected = Buffer.from(
    createHmac("sha256", signingSecret).update(rawBody).digest("hex"),
    "hex",
  );
  let actualHex = providedSignature.trim();
  if (actualHex.startsWith("sha256=")) actualHex = actualHex.slice(7);
  if (!/^[0-9a-f]{64}$/i.test(actualHex)) return false;
  const actual = Buffer.from(actualHex, "hex");
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}

async function handle(request, response) {
  const url = new URL(request.url || "/", "http://localhost");

  if (request.method === "GET" && url.pathname === "/") {
    response.writeHead(200, {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
    });
    response.end(page);
    return;
  }

  if (request.method === "GET" && url.pathname === "/api/status") {
    sendJson(response, 200, { secretConfigured: Boolean(signingSecret) });
    return;
  }

  if (request.method === "POST" && url.pathname === "/api/secret") {
    try {
      const body = JSON.parse((await readBody(request)).toString("utf8"));
      signingSecret = typeof body.secret === "string" ? body.secret.trim() : "";
      sendJson(response, 200, { secretConfigured: Boolean(signingSecret) });
    } catch (error) {
      sendJson(response, error.status || 400, { message: error.message || "Invalid JSON" });
    }
    return;
  }

  if (request.method === "GET" && url.pathname === "/api/events") {
    sendJson(response, 200, deliveries);
    return;
  }

  if (request.method === "DELETE" && url.pathname === "/api/events") {
    deliveries.length = 0;
    sendJson(response, 200, { cleared: true });
    return;
  }

  if (request.method === "POST" && url.pathname === "/webhooks/flowpay") {
    let rawBody;
    try {
      rawBody = await readBody(request);
    } catch (error) {
      sendJson(response, error.status || 400, { message: error.message });
      return;
    }

    const signature = request.headers["x-flowpay-signature"] || "";
    const verified = signatureMatches(rawBody, signature);
    let payload;
    try {
      payload = JSON.parse(rawBody.toString("utf8"));
    } catch {
      payload = rawBody.toString("utf8");
    }

    const delivery = {
      receivedAt: new Date().toISOString(),
      eventId: request.headers["x-flowpay-event-id"] || payload?.id || "unknown",
      eventType: request.headers["x-flowpay-event"] || payload?.type || "unknown",
      deliveryId: request.headers["x-flowpay-delivery-id"] || "",
      verification: !signingSecret ? "SECRET NOT SET" : verified ? "VERIFIED" : "INVALID SIGNATURE",
      payload,
    };
    deliveries.unshift(delivery);
    if (deliveries.length > MAX_EVENTS) deliveries.length = MAX_EVENTS;

    if (!signingSecret) {
      sendJson(response, 401, { message: "Set this merchant's webhook secret in the receiver inbox first." });
      return;
    }
    if (!verified) {
      sendJson(response, 401, { message: "Webhook signature did not match the configured secret." });
      return;
    }
    sendJson(response, 200, { received: true, eventId: delivery.eventId });
    return;
  }

  sendJson(response, 404, { message: "Not found" });
}

const hosts = process.env.HOST ? [process.env.HOST] : ["127.0.0.1", "::1"];
for (const host of hosts) {
  const server = createServer((request, response) => {
    handle(request, response).catch((error) => {
      console.error("Receiver request failed:", error);
      if (!response.headersSent) sendJson(response, 500, { message: "Receiver error" });
      else response.destroy();
    });
  });
  server.on("error", (error) => {
    console.error(`Could not listen on ${host}:${PORT}: ${error.message}`);
  });
  server.listen(PORT, host, () => {
    console.log(`FlowPay webhook inbox listening at http://${host}:${PORT}`);
  });
}
