package com.cfs.FlowPay.Reconciliation;

import com.cfs.FlowPay.Common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "reconciliation_issues", indexes = @Index(name = "idx_reconciliation_merchant_status", columnList = "merchantId,status"))
public class ReconciliationIssue extends BaseEntity {
    @Column(nullable = false, unique = true, length = 140) public String issueKey;
    @Column(nullable = false, length = 48) public String merchantId;
    @Column(nullable = false, length = 48) public String referenceId;
    @Column(nullable = false, length = 60) public String issueType;
    @Column(nullable = false, length = 20) public String status = "OPEN";
    @Column(nullable = false, length = 500) public String details;
    public Long expectedAmount;
    public Long actualAmount;
}
