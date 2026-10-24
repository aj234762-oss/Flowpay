package com.cfs.FlowPay.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReconciliationIssueRepository extends JpaRepository<ReconciliationIssue, String> {
    Optional<ReconciliationIssue> findByIssueKey(String issueKey);
    List<ReconciliationIssue> findByMerchantIdOrderByCreatedAtDesc(String merchantId);
    List<ReconciliationIssue> findByMerchantIdAndStatusOrderByCreatedAtDesc(String merchantId, String status);
}
