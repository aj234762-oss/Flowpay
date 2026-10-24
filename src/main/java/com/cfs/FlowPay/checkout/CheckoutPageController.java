package com.cfs.FlowPay.checkout;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class CheckoutPageController {
    @GetMapping("/checkout/{token}")
    public String checkout(@PathVariable String token) {
        return "forward:/checkout.html";
    }
}
