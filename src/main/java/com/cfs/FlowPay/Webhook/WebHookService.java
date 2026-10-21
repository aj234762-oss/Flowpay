package com.cfs.FlowPay.Webhook;

import com.cfs.FlowPay.Common.ApiException;
import com.cfs.FlowPay.Common.Ids;
import com.cfs.FlowPay.Merchant.Merchant;
import com.cfs.FlowPay.Merchant.MerchantRepository;
import com.cfs.FlowPay.kafka.Event;
import jakarta.transaction.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.Duration;
import java.time.Instant;

/** Persists signed webhook work in the same database transaction as its payment event. */
@Service
public class WebHookService {
    private final WebhookRepository webhooks;
    private final MerchantRepository merchants;

    public WebHookService(WebhookRepository webhooks, MerchantRepository merchants) {
        this.webhooks = webhooks;
        this.merchants = merchants;
    }

    @Transactional
    public void enqueue(Event event) {
        Merchant merchant = merchants.findById(event.merchantId).orElse(null);
        if (merchant == null || merchant.webhookUrl == null || merchant.webhookUrl.isBlank()) return;
        Webhook hook = new Webhook();
        hook.id = Ids.next("wh"); hook.eventId = event.id; hook.merchantId = event.merchantId;
        hook.url = merchant.webhookUrl; hook.signingSecret = merchant.webhookSecret;
        hook.eventType = event.type; hook.payload = event.payload;
        hook.signature = sign(merchant.webhookSecret, event.payload);
        webhooks.save(hook);
    }

    private static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Could not sign webhook payload", ex); }
    }

    public List<Webhook> list(String merchantId) {
        return webhooks.findByMerchantIdOrderByCreateAtDesc(merchantId);
    }

    /** Persist the result and schedule a bounded retry for a non-2xx response or network error. */
    @Transactional
    public void recordAttempt(String id, Integer responseCode, String error, int maxAttempts, Duration[] retryDelays) {
        Webhook hook = webhooks.lockById(id).orElse(null);
        if (hook == null || !"QUEUED".equals(hook.status)) return;
        hook.attemptCount++;
        hook.responseCode = responseCode;
        hook.lastError = error;
        if (error == null) {
            hook.status = "DELIVERED";
            hook.deliveredAt = Instant.now();
            hook.nextAttempt = null;
        } else if (hook.attemptCount >= maxAttempts) {
            hook.status = "FAILED";
            hook.nextAttempt = null;
        } else {
            hook.status = "QUEUED";
            int delayIndex = Math.min(hook.attemptCount - 1, retryDelays.length - 1);
            hook.nextAttempt = Instant.now().plus(retryDelays[delayIndex]);
        }
    }

    /** Requeue a failed delivery for a configured delivery worker. */
    @Transactional
    public Webhook retry(String merchantId, String id) {
        Webhook hook = webhooks.lockById(id).orElseThrow(ApiException::missing);
        if (!hook.merchantId.equals(merchantId)) throw ApiException.missing();
        if ("DELIVERED".equals(hook.status)) throw ApiException.conflict("Delivered webhooks cannot be replayed");
        hook.status = "QUEUED"; hook.attemptCount = 0; hook.lastError = null;
        hook.responseCode = null; hook.nextAttempt = java.time.Instant.now();
        return hook;
    }
}
