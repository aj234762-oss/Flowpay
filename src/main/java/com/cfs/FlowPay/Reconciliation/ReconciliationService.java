package com.cfs.FlowPay.Reconciliation;

import com.cfs.FlowPay.Common.Ids;
import com.cfs.FlowPay.Payment.Payment;
import com.cfs.FlowPay.Payment.PaymentRepository;
import com.cfs.FlowPay.Transaction.LedgerEntry;
import com.cfs.FlowPay.Transaction.LedgerEntryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReconciliationService {
    private final PaymentRepository payments;
    private final LedgerEntryRepository ledger;
    private final ReconciliationIssueRepository issues;

    public ReconciliationService(PaymentRepository payments, LedgerEntryRepository ledger,
                                 ReconciliationIssueRepository issues) {
        this.payments = payments; this.ledger = ledger; this.issues = issues;
    }

    @Transactional
    public ReconciliationDtos.Report run(String merchantId) {
        List<Payment> rows = payments.findByMerchantIdOrderByCreateAtDesc(merchantId);
        List<ReconciliationIssue> found = new ArrayList<>();
        for (Payment payment : rows) {
            String key = merchantId + ":" + payment.id + ":PAYMENT_LEDGER";
            LedgerEntry entry = ledger.findByReferenceIdAndType(payment.id, "PAYMENT").orElse(null);
            boolean shouldExist = "SUCCESS".equals(payment.status);
            long expected = shouldExist ? payment.amount - payment.fee : 0;
            boolean mismatch = shouldExist ? entry == null || entry.amount != expected : entry != null;
            ReconciliationIssue issue = issues.findByIssueKey(key);
            if (mismatch) {
                if (issue == null) {
                    issue = new ReconciliationIssue(); issue.id = Ids.next("rec"); issue.issueKey = key;
                    issue.merchantId = merchantId; issue.referenceId = payment.id; issue.issueType = "PAYMENT_LEDGER_MISMATCH";
                }
                issue.status = "OPEN";
                issue.expectedAmount = shouldExist ? expected : null;
                issue.actualAmount = entry == null ? null : entry.amount;
                issue.details = shouldExist ? (entry == null ? "Successful payment is missing its merchant credit" : "Merchant credit does not match payment net amount") : "A ledger credit exists for a payment that is not successful";
                issues.save(issue); found.add(issue);
            } else if (issue != null && "OPEN".equals(issue.status)) {
                issue.status = "RESOLVED"; issues.save(issue);
            }
        }
        return new ReconciliationDtos.Report(Instant.now(), rows.size(), found.size(), found);
    }

    public List<ReconciliationIssue> list(String merchantId, String status) {
        return status == null || status.isBlank() ? issues.findByMerchantIdOrderByCreateAtDesc(merchantId)
                : issues.findByMerchantIdAndStatusOrderByCreateAtDesc(merchantId, status.toUpperCase());
    }

    @Transactional
    public void runAll(List<String> merchantIds) { for (String merchantId : merchantIds) run(merchantId); }
}
