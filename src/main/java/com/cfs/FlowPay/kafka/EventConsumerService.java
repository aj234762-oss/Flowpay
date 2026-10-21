package com.cfs.FlowPay.kafka;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.time.Instant;

/** Marks a broker event as consumed. Webhook intent is recorded atomically when the outbox event is created. */
@Service
public class EventConsumerService {
    private final EventRepository events;
    public EventConsumerService(EventRepository events) { this.events = events; }

    @Transactional
    public void consume(String id) {
        Event event = events.lockById(id).orElseThrow();
        if (event.consumedAt == null) event.consumedAt = Instant.now();
    }
}
