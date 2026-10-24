package com.cfs.FlowPay.reconciliation;

import com.cfs.FlowPay.common.Ids;
import com.cfs.FlowPay.payment.Payment;
import com.cfs.FlowPay.payment.PaymentRepository;
import com.cfs.FlowPay.transaction.LedgerEntry;
import com.cfs.FlowPay.transaction.LedgerEntryRepository;
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
        this.payments = payments;
        this.ledger = ledger;
        this.issues = issues;
    }

    @Transactional
    public ReconciliationDtos.Report run(String merchantId) {
        List<Payment> rows = payments.findByMerchantIdOrderByCreatedAtDesc(merchantId);
        List<ReconciliationIssue> found = new ArrayList<>();
        for (Payment payment : rows) {
            String issueKey = merchantId + ":" + payment.id + ":PAYMENT_LEDGER";
            LedgerEntry entry = ledger.findByReferenceIdAndType(payment.id, "PAYMENT").orElse(null);
            boolean shouldHaveCredit = "SUCCESS".equals(payment.status);
            long expectedAmount = shouldHaveCredit ? payment.amount - payment.fee : 0;
            boolean mismatch = shouldHaveCredit
                    ? entry == null || entry.amount != expectedAmount
                    : entry != null;

            ReconciliationIssue issue = issues.findByIssueKey(issueKey).orElse(null);
            if (mismatch) {
                if (issue == null) {
                    issue = new ReconciliationIssue();
                    issue.id = Ids.next("rec");
                    issue.issueKey = issueKey;
                    issue.merchantId = merchantId;
                    issue.referenceId = payment.id;
                    issue.issueType = "PAYMENT_LEDGER_MISMATCH";
                }
                issue.status = "OPEN";
                issue.expectedAmount = shouldHaveCredit ? expectedAmount : null;
                issue.actualAmount = entry == null ? null : entry.amount;
                issue.details = shouldHaveCredit
                        ? entry == null ? "Successful payment is missing its merchant credit"
                        : "Merchant credit does not match the payment net amount"
                        : "A ledger credit exists for a payment that is not successful";
                issues.save(issue);
                found.add(issue);
            } else if (issue != null && "OPEN".equals(issue.status)) {
                issue.status = "RESOLVED";
                issue.expectedAmount = null;
                issue.actualAmount = entry == null ? null : entry.amount;
                issue.details = "Payment and ledger are consistent";
                issues.save(issue);
            }
        }
        return new ReconciliationDtos.Report(Instant.now(), rows.size(), found.size(), found);
    }

    @Transactional
    public void runAll(List<String> merchantIds) {
        for (String merchantId : merchantIds) run(merchantId);
    }

    public List<ReconciliationIssue> list(String merchantId, String status) {
        return status == null || status.isBlank()
                ? issues.findByMerchantIdOrderByCreatedAtDesc(merchantId)
                : issues.findByMerchantIdAndStatusOrderByCreatedAtDesc(merchantId, status.toUpperCase());
    }
}
