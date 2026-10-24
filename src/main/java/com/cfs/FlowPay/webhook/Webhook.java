package com.cfs.FlowPay.webhook;

import com.cfs.FlowPay.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "webhooks")
public class Webhook extends BaseEntity {
    @Column(nullable = false, length = 48, unique = true)
    public String eventId;

    @Column(nullable = false, length = 48)
    public String merchantId;

    @Column(length = 500)
    public String url;

    // Nullable so existing delivery rows remain compatible with Hibernate schema updates.
    @Column(length = 60)
    public String eventType;

    @Column(columnDefinition = "TEXT")
    public String payload;

    @Column(length = 80)
    public String signature;

    @Column(nullable = false, length = 20)
    public String status = "PENDING";

    @Column(nullable = false)
    public int attempts;

    @Column(length = 500)
    public String lastError;

    public Integer responseCode;
    public Instant nextAttemptAt = Instant.now();
    public Instant deliveredAt;
}
