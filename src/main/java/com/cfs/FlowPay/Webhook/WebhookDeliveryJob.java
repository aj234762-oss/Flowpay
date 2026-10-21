package com.cfs.FlowPay.Webhook;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/** Delivers queued webhook outbox records and retries transient failures. */
@Component
public class WebhookDeliveryJob {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration[] RETRY_DELAYS = {
            Duration.ofSeconds(15), Duration.ofSeconds(60),
            Duration.ofMinutes(5), Duration.ofMinutes(15)
    };

    private final WebhookRepository webhooks;
    private final WebHookService service;
    private final HttpClient client;
    private final AtomicBoolean running = new AtomicBoolean();

    public WebhookDeliveryJob(WebhookRepository webhooks, WebHookService service) {
        this.webhooks = webhooks;
        this.service = service;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Scheduled(fixedDelayString = "${app.webhook.delivery-interval-ms:5000}")
    public void deliverDue() {
        if (!running.compareAndSet(false, true)) return;
        try {
            var due = webhooks.findByStatusAndNextAttemptLessThanEqualOrderByNextAttemptAscCreateAtAsc(
                    "QUEUED", Instant.now(), PageRequest.of(0, 20));
            for (Webhook hook : due) deliver(hook);
        } finally {
            running.set(false);
        }
    }

    private void deliver(Webhook hook) {
        Integer responseCode = null;
        String error = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(hook.url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "FlowPay-Webhooks/1.0")
                    .header("X-FlowPay-Event", hook.eventType)
                    .header("X-FlowPay-Event-Id", hook.eventId)
                    .header("X-FlowPay-Delivery-Id", hook.id)
                    .header("X-FlowPay-Signature", hook.signature)
                    .POST(HttpRequest.BodyPublishers.ofString(hook.payload))
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            responseCode = response.statusCode();
            if (responseCode < 200 || responseCode >= 300) error = "Receiver returned HTTP " + responseCode;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            error = "Delivery interrupted";
        } catch (Exception ex) {
            error = "Delivery failed: " + ex.getClass().getSimpleName();
        }
        service.recordAttempt(hook.id, responseCode, error, MAX_ATTEMPTS, RETRY_DELAYS);
    }
}
