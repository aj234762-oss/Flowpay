package com.cfs.FlowPay.reconciliation;

import com.cfs.FlowPay.merchant.MerchantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {
    private final MerchantService merchants;
    private final ReconciliationService reconciliation;

    public ReconciliationController(MerchantService merchants, ReconciliationService reconciliation) {
        this.merchants = merchants;
        this.reconciliation = reconciliation;
    }

    @PostMapping("/run")
    public ReconciliationDtos.Report run(@RequestHeader("X-Api-Key") String key) {
        return reconciliation.run(merchants.requireMutation(key).id);
    }

    @GetMapping("/issues")
    public List<ReconciliationIssue> issues(@RequestHeader("X-Api-Key") String key,
                                            @RequestParam(required = false) String status) {
        return reconciliation.list(merchants.authenticate(key).id, status);
    }
}
