package com.cfs.FlowPay.Reconciliation;

import com.cfs.FlowPay.Merchant.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationJobs {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationJobs.class);
    private final MerchantRepository merchants;
    private final ReconciliationService reconciliation;
    public ReconciliationJobs(MerchantRepository merchants, ReconciliationService reconciliation) { this.merchants = merchants; this.reconciliation = reconciliation; }
    @Scheduled(fixedDelayString = "${app.reconciliation-poll-ms:60000}")
    public void run() {
        try { reconciliation.runAll(merchants.findAll().stream().map(m -> m.id).toList()); }
        catch (Exception ex) { log.warn("Reconciliation run failed: {}", ex.getClass().getSimpleName()); }
    }
}
