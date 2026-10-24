package com.cfs.FlowPay.auth;

import com.cfs.FlowPay.common.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "dashboard_users")
public class DashboardUser extends BaseEntity {
    @Column(nullable = false, length = 48)
    public String merchantId;

    @Column(nullable = false, unique = true, length = 160)
    public String email;

    @JsonIgnore
    @Column(nullable = false, length = 100)
    public String passwordHash;

    @Column(nullable = false, length = 16)
    public String role = "OWNER";
}
