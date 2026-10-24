package com.cfs.FlowPay.checkout;

import com.cfs.FlowPay.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "checkout_sessions", indexes =
        @Index(name = "idx_checkout_sessions_merchant", columnList = "merchantId,createdAt"))
public class CheckoutSession extends BaseEntity {
    @Column(nullable = false, length = 48)
    public String merchantId;

    @Column(nullable = false, length = 48)
    public String orderId;

    @Column(nullable = false, unique = true, length = 80)
    public String token;

    @Column(nullable = false, length = 20)
    public String status = "ACTIVE";

    @Column(length = 48)
    public String paymentId;

    @Column(nullable = false)
    public Instant expiresAt;
}
