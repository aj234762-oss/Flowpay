package com.cfs.FlowPay.bank;

import com.cfs.FlowPay.Payment.PaymentRepository;
import com.cfs.FlowPay.Payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name="app.jobs-enabled", havingValue="true")
public class BankJobs {

    private static final Logger log = LoggerFactory.getLogger(BankJobs.class);
    private static final List<String> RECONCILABLE_SCENARIOS = List.of("DELAYED_SUCCESS", "TIMEOUT");

    private final PaymentRepository repo;
    private final PaymentService service;

    public BankJobs(PaymentRepository repo, PaymentService service) {
        this.repo = repo;
        this.service = service;
    }

    @Scheduled(fixedDelay = 2000)
    public void completeDelayed() {
        Instant now = Instant.now();
        for (String scenario : RECONCILABLE_SCENARIOS) {
            var duePayments = repo.findTop50ByStatusAndScenarioAndResolveAtBeforeOrderByCreateAtAsc(
                    "PENDING", scenario, now);
            for (var payment : duePayments) {
                try {
                    service.reconcile(payment.merchantId, payment.id);
                } catch (Exception ex) {
                    log.warn("Could not reconcile payment {}: {}", payment.id, ex.getClass().getSimpleName());
                }
            }
        }
    }
}
