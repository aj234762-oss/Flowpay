package com.cfs.FlowPay.auth;

import com.cfs.FlowPay.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dashboard_sessions")
public class DashboardSession extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    public String tokenHash;

    @Column(nullable = false, length = 48)
    public String userId;

    @Column(nullable = false, length = 48)
    public String merchantId;

    @Column(nullable = false)
    public Instant expiresAt;
}
