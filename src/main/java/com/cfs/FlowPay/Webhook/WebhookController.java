package com.cfs.FlowPay.Webhook;

import com.cfs.FlowPay.Merchant.MerchantService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {
    private final WebHookService webhooks;
    private final MerchantService merchants;
    public WebhookController(WebHookService webhooks, MerchantService merchants) { this.webhooks = webhooks; this.merchants = merchants; }
    @GetMapping public List<Webhook> list(@RequestHeader("X-Api-Key") String key) { return webhooks.list(merchants.authenticate(key).id); }
    @PostMapping("/{id}/retry") public Webhook retry(@RequestHeader("X-Api-Key") String key, @PathVariable String id) { return webhooks.retry(merchants.authenticate(key).id, id); }
}
