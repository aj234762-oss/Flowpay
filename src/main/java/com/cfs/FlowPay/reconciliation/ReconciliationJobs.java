package com.cfs.FlowPay.reconciliation;

import com.cfs.FlowPay.merchant.MerchantRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.jobs-enabled", havingValue = "true")
public class ReconciliationJobs {
    private final MerchantRepository merchants;
    private final ReconciliationService reconciliation;

    public ReconciliationJobs(MerchantRepository merchants, ReconciliationService reconciliation) {
        this.merchants = merchants;
        this.reconciliation = reconciliation;
    }

    @Scheduled(fixedDelayString = "${app.reconciliation-poll-ms:60000}")
    public void runPeriodicChecks() {
        reconciliation.runAll(merchants.findAll().stream().map(merchant -> merchant.id).toList());
    }
}
