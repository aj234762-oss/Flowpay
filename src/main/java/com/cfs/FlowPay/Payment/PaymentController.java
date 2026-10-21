package com.cfs.FlowPay.Payment;

import com.cfs.FlowPay.Merchant.MerchantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService service;
    private final MerchantService merchants;

    public PaymentController(PaymentService service, MerchantService merchants) {
        this.service = service;
        this.merchants = merchants;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Payment create(@RequestHeader("X-Api-Key") String key,
                          @RequestHeader("Idempotency-key") String ik,
                          @Valid @RequestBody PaymentDtos.Create r
                          )
    {
        return service.create(merchants.authenticate(key).id,ik,r);
    }

    @PostMapping("/{id}")
    public Payment get(@RequestHeader("X-Api-key") String key,@PathVariable String id)
    {
        return service.get(merchants.authenticate(key).id,id);
    }

    @PostMapping("/{id}/process")
    public Payment process(@RequestHeader("X-Api-key") String key,@PathVariable String id)
    {
        return service.process(merchants.authenticate(key).id,id);
    }
    @PostMapping("/{id}/check")
    public Payment check(@RequestHeader("X-Api-key") String key,@PathVariable String id)
    {
        return service.reconcile(merchants.authenticate(key).id,id);
    }
}
