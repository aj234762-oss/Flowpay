package com.cfs.FlowPay.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "app.jobs-enabled", havingValue = "true")
public class WebhookJobs {
    private final WebhookRepository repository;
    private final WebhookService service;

    public WebhookJobs(WebhookRepository repository, WebhookService service) {
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(fixedDelay = 2000)
    public void deliver() {
        for (Webhook webhook : repository.findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                "PENDING", Instant.now())) {
            service.deliver(webhook.id);
        }
    }
}
