package com.cfs.FlowPay.Transaction;

import com.cfs.FlowPay.Merchant.MerchantService;
import com.cfs.FlowPay.Payment.Payment;
import com.cfs.FlowPay.Payment.PaymentService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;
    private final MerchantService merchants;
    private final PaymentService payments;


    public TransactionController(TransactionService service, MerchantService merchants, PaymentService payments) {
        this.service = service;
        this.merchants = merchants;
        this.payments = payments;
    }

    @GetMapping
    public List<Payment> list(@RequestHeader("X-Api-key") String key,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) LocalDate from ,
                              @RequestParam(required = false) LocalDate to
                              )
    {
        return service.list(merchants.authenticate(key).id,status,from,to);
    }

    @GetMapping("/ledger")
    public List<LedgerEntry> ledger(@RequestHeader("X-Api-key") String key)
    {
        return service.ledger(merchants.authenticate(key).id);
    }

    @GetMapping("/{id}")
    public Payment details(@RequestHeader("X-Api-key") String key,
                           @PathVariable String id)
    {
        return payments.get(merchants.authenticate(key).id,id);
    }
}
