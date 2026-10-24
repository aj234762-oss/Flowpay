package com.cfs.FlowPay.checkout;

import com.cfs.FlowPay.merchant.MerchantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class CheckoutSessionController {
    private final CheckoutSessionService service;
    private final MerchantService merchants;

    public CheckoutSessionController(CheckoutSessionService service, MerchantService merchants) {
        this.service = service;
        this.merchants = merchants;
    }

    public record CreateRequest(@NotBlank String orderId) {}

    @PostMapping("/api/checkout-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutSessionService.Created create(@RequestHeader("X-Api-Key") String key,
                                                  @Valid @RequestBody CreateRequest request) {
        return service.create(merchants.requireMutation(key).id, request.orderId());
    }

    @GetMapping("/api/public/checkout/{token}")
    public CheckoutDtos.View view(@PathVariable String token) {
        return service.view(token);
    }

    @PostMapping("/api/public/checkout/{token}/pay")
    public CheckoutDtos.View pay(@PathVariable String token, @Valid @RequestBody CheckoutDtos.Pay request) {
        return service.pay(token, request);
    }

    @PostMapping("/api/public/checkout/{token}/refresh")
    public CheckoutDtos.View refresh(@PathVariable String token) {
        return service.view(token);
    }
}
