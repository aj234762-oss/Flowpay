package com.cfs.FlowPay.Webhook;

import com.cfs.FlowPay.Common.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "webhooks", indexes = @Index(name = "idx_webhooks_due", columnList = "status,nextAttempt"))
public class Webhook extends BaseEntity {
    @Column(nullable = false, length = 48, unique = true) public String eventId;
    @Column(nullable = false, length = 48) public String merchantId;
    @Column(nullable = false, length = 500) public String url;
    @JsonIgnore @Column(nullable = false, length = 100) public String signingSecret;
    @Column(nullable = false, length = 60) public String eventType;
    @Column(nullable = false, columnDefinition = "text") public String payload;
    @Column(nullable = false, length = 71) public String signature;
    @Column(nullable = false, length = 20) public String status = "QUEUED";
    @Column(length = 500) public String lastError;
    @Column(nullable = false) public int attemptCount;
    public Integer responseCode;
    public Instant nextAttempt = Instant.now();
    public Instant deliveredAt;
}
