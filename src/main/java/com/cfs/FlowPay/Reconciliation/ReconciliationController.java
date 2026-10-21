package com.cfs.FlowPay.Reconciliation;

import com.cfs.FlowPay.Merchant.MerchantService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {
    private final MerchantService merchants;
    private final ReconciliationService reconciliation;
    public ReconciliationController(MerchantService merchants, ReconciliationService reconciliation) { this.merchants = merchants; this.reconciliation = reconciliation; }
    @PostMapping("/run") public ReconciliationDtos.Report run(@RequestHeader("X-Api-Key") String key) { return reconciliation.run(merchants.authenticate(key).id); }
    @GetMapping("/issues") public List<ReconciliationIssue> issues(@RequestHeader("X-Api-Key") String key, @RequestParam(required = false) String status) { return reconciliation.list(merchants.authenticate(key).id, status); }
}
