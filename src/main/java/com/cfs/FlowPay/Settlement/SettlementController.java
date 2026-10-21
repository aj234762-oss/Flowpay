package com.cfs.FlowPay.Settlement;

import com.cfs.FlowPay.Merchant.MerchantService;
import com.cfs.FlowPay.Refund.Refund;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementService service;
    private final MerchantService merchants;

    public SettlementController(SettlementService service, MerchantService merchants) {
        this.service = service;
        this.merchants = merchants;
    }

    @PostMapping
    public Settlement create(@RequestHeader("X-Api-Key") String key,
                         @RequestHeader("Idempotency-Key") String ik
                         )
    {
        return service.create(merchants.authenticate(key).id,ik);
    }

    @GetMapping
    public List<Settlement> list(@RequestHeader("X-Api-Key") String key){
        return service.list(merchants.authenticate(key).id);
    }
}
