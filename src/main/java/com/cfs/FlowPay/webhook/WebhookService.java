package com.cfs.FlowPay.webhook;

import com.cfs.FlowPay.common.ApiException;
import com.cfs.FlowPay.kafka.Event;
import com.cfs.FlowPay.kafka.EventRepository;
import com.cfs.FlowPay.merchant.Merchant;
import com.cfs.FlowPay.merchant.MerchantRepository;
import com.cfs.FlowPay.merchant.MerchantService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class WebhookService {
    private static final Duration[] RETRY_DELAYS = {
            Duration.ofSeconds(15), Duration.ofSeconds(60), Duration.ofMinutes(5), Duration.ofMinutes(15)
    };

    private final WebhookRepository repo;
    private final EventRepository events;
    private final MerchantRepository merchants;
    private final MerchantService merchantService;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public WebhookService(WebhookRepository repo, EventRepository events,
                          MerchantRepository merchants, MerchantService merchantService) {
        this.repo = repo;
        this.events = events;
        this.merchants = merchants;
        this.merchantService = merchantService;
    }

    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Could not sign webhook payload", error);
        }
    }

    @Transactional
    public void deliver(String id) {
        Webhook webhook = repo.lockById(id).orElseThrow();
        if (!"PENDING".equals(webhook.status) || webhook.nextAttemptAt.isAfter(Instant.now())) return;
        Event event = events.findById(webhook.eventId).orElseThrow();
        Merchant merchant = merchants.findById(webhook.merchantId).orElseThrow();

        // Snapshot older rows the first time they are sent; new rows are populated by the Kafka consumer.
        if (webhook.eventType == null) webhook.eventType = event.type;
        if (webhook.payload == null) webhook.payload = event.payload;
        // Sign with the currently active secret so queued retries continue to work after rotation.
        webhook.signature = sign(merchant.webhookSecret, webhook.payload);

        webhook.attempts++;
        webhook.responseCode = null;
        try {
            merchantService.validate(webhook.url);
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhook.url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "FlowPay-Webhooks/1.0")
                    .header("X-FlowPay-Event", webhook.eventType)
                    .header("X-FlowPay-Event-Id", webhook.eventId)
                    .header("X-FlowPay-Delivery-Id", webhook.id)
                    .header("X-FlowPay-Signature", webhook.signature)
                    .POST(HttpRequest.BodyPublishers.ofString(webhook.payload))
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            webhook.responseCode = status;
            if (status >= 200 && status < 300) {
                webhook.status = "DELIVERED";
                webhook.lastError = null;
                webhook.deliveredAt = Instant.now();
                return;
            }
            failAttempt(webhook, "Receiver returned HTTP " + status);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            failAttempt(webhook, "Delivery interrupted");
        } catch (Exception error) {
            failAttempt(webhook, error.getClass().getSimpleName());
        }
    }

    private static void failAttempt(Webhook webhook, String reason) {
        webhook.lastError = reason;
        webhook.deliveredAt = null;
        if (webhook.attempts >= 5) {
            webhook.status = "FAILED";
            webhook.nextAttemptAt = null;
            return;
        }
        webhook.status = "PENDING";
        int delayIndex = Math.min(webhook.attempts - 1, RETRY_DELAYS.length - 1);
        webhook.nextAttemptAt = Instant.now().plus(RETRY_DELAYS[delayIndex]);
    }

    @Transactional
    public Webhook retry(String merchantId, String id) {
        Webhook webhook = repo.lockById(id).orElseThrow(ApiException::missing);
        if (!webhook.merchantId.equals(merchantId)) throw ApiException.missing();
        if ("DELIVERED".equals(webhook.status)) throw ApiException.conflict("Webhook already delivered");
        Merchant merchant = merchants.findById(merchantId).orElseThrow();
        if (merchant.webhookUrl == null || merchant.webhookUrl.isBlank()) {
            throw ApiException.bad("Configure a webhook URL first");
        }
        webhook.url = merchant.webhookUrl;
        webhook.status = "PENDING";
        webhook.attempts = 0;
        webhook.responseCode = null;
        webhook.nextAttemptAt = Instant.now();
        webhook.lastError = null;
        webhook.deliveredAt = null;
        return webhook;
    }

    public List<Webhook> list(String merchantId) {
        return repo.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }
}
