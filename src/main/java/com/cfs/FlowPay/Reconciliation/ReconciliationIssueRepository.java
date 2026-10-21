package com.cfs.FlowPay.Reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReconciliationIssueRepository extends JpaRepository<ReconciliationIssue, String> {
    boolean existsByIssueKey(String issueKey);
    ReconciliationIssue findByIssueKey(String issueKey);
    List<ReconciliationIssue> findByMerchantIdOrderByCreateAtDesc(String merchantId);
    List<ReconciliationIssue> findByMerchantIdAndStatusOrderByCreateAtDesc(String merchantId, String status);
}
