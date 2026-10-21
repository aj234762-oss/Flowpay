package com.cfs.FlowPay.Reconciliation;

import java.time.Instant;
import java.util.List;

public final class ReconciliationDtos {
    private ReconciliationDtos() {}
    public record Report(Instant checkedAt, int paymentsChecked, int issuesFound, List<ReconciliationIssue> issues) {}
}
