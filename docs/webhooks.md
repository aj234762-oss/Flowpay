# Webhooks

FlowPay creates webhook events after simulated payment, refund, and settlement changes. Kafka transports the internal event to a consumer, which persists a delivery record. A scheduled database-backed worker then sends the signed HTTP request and retries failures. The **Webhooks** dashboard page shows delivery status; the local receiver inbox shows the received body and HMAC verification result.

## Local receiver setup

With `docker compose up --build`, open <http://localhost:8081> and enter the merchant's signing secret. Set that merchant's webhook URL in **Settings** to `http://receiver:8081/webhooks/flowpay`. The hostname `receiver` is the Docker Compose service name; use this URL for backend-to-receiver traffic inside Compose. The browser inbox remains at `localhost:8081`.

When the backend runs directly on the host, set the URL to `http://localhost:8081/webhooks/flowpay` and run `npm start` from `receiver/` in a separate terminal. If the backend runs in a container while the receiver runs on the host, use `http://host.docker.internal:8081/webhooks/flowpay` and allow that hostname in `WEBHOOK_HOSTS`.

The browser inbox accepts the signing secret under **Signing secret**. Enter the same secret that was displayed once when the merchant was registered. The receiver keeps it in memory, so re-enter it after restarting the receiver. Do not put the secret in the webhook URL or share it.

## Request format

- Method: `POST`
- Content type: `application/json`
- `X-FlowPay-Event`: event type, such as `payment.succeeded`, `payment.failed`, `refund.created`, or `settlement.created`
- `X-FlowPay-Event-Id`: stable event identifier; use this for deduplication
- `X-FlowPay-Delivery-Id`: identifier for this delivery record
- `X-FlowPay-Signature`: `sha256=` plus lowercase hex HMAC-SHA256 of the exact raw body

Verify against the exact bytes received, before parsing JSON. Use a constant-time comparison. Respond with any `2xx` status to acknowledge the delivery. Network errors and non-`2xx` responses are retried with increasing delays, up to five attempts; failed deliveries can be manually retried from the dashboard. Since a receiver may see duplicates, make event processing idempotent by event ID.
