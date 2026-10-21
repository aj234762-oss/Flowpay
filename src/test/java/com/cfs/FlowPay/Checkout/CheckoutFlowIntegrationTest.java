package com.cfs.FlowPay.Checkout;

import com.cfs.FlowPay.Common.ApiException;
import com.cfs.FlowPay.Merchant.MerchantDtos;
import com.cfs.FlowPay.Merchant.MerchantService;
import com.cfs.FlowPay.Merchant.MerchantRepository;
import com.cfs.FlowPay.Order.OrderDtos;
import com.cfs.FlowPay.Order.OrderService;
import com.cfs.FlowPay.Payment.PaymentDtos;
import com.cfs.FlowPay.Payment.PaymentService;
import com.cfs.FlowPay.Payment.PaymentRepository;
import java.time.Instant;
import com.cfs.FlowPay.Webhook.WebhookRepository;
import com.cfs.FlowPay.Refund.RefundDtos;
import com.cfs.FlowPay.Refund.RefundService;
import com.cfs.FlowPay.Settlement.SettlementService;
import com.cfs.FlowPay.kafka.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.hamcrest.Matchers.containsString;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutFlowIntegrationTest {
    @Autowired MockMvc http;
    @Autowired MerchantService merchants;
    @Autowired MerchantRepository merchantRepository;
    @Autowired OrderService orders;
    @Autowired CheckoutSessionService checkouts;
    @Autowired PaymentService payments;
    @Autowired PaymentRepository paymentRepository;
    @Autowired EventRepository events;
    @Autowired RefundService refunds;
    @Autowired SettlementService settlements;
    @Autowired WebhookRepository webhooks;

    @Test void dashboardAndHostedCheckoutPagesAreServed() {
        try {
            http.perform(get("/")).andExpect(status().isOk());
            http.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().string(containsString("Payment overview")));
            http.perform(get("/checkout/checkout_test-token")).andExpect(status().isOk()).andExpect(forwardedUrl("/checkout.html"));
            http.perform(get("/checkout.html")).andExpect(status().isOk()).andExpect(content().string(containsString("Secure checkout")));
        } catch (Exception ex) { throw new AssertionError("Dashboard or checkout page was not served", ex); }
    }

    @Test void hostedCheckoutCreatesPaymentLedgerEventAndWebhookRecord() {
        var registered = merchants.register(new MerchantDtos.Register("Checkout Test", "checkout-test@example.com"));
        String merchantId = registered.merchant().id;
        merchants.update(merchantId, new MerchantDtos.Update("Checkout Test", "checkout-test@example.com", "http://localhost:9999/webhook"));
        var order = orders.create(merchantId, new OrderDtos.Create(12000, "checkout-test-" + System.nanoTime()));
        var checkout = checkouts.create(merchantId, order.id);
        assertTrue(checkout.checkoutUrl().contains(checkout.session().token));

        var result = checkouts.pay(checkout.session().token, new CheckoutDtos.Pay(PaymentDtos.Method.UPI, PaymentDtos.Scenario.SUCCESS));
        assertEquals("SUCCESS", result.paymentStatus());
        assertEquals("COMPLETED", result.status());
        assertEquals("PAID", orders.get(merchantId, order.id).status);
        assertEquals(12000 - PaymentService.fee(12000), merchantRepository.findById(merchantId).orElseThrow().balance);
        assertTrue(events.findAll().stream().anyMatch(e -> "payment.succeeded".equals(e.type)));
        assertTrue(webhooks.findByMerchantIdOrderByCreateAtDesc(merchantId).stream().anyMatch(w -> "QUEUED".equals(w.status) && w.signature.startsWith("sha256=")));
    }

    @Test void delayedAndTimeoutPaymentsReachOnlyTheirExpectedTerminalState() {
        var registered = merchants.register(new MerchantDtos.Register("Scenario Test", "scenario-test@example.com"));
        String merchantId = registered.merchant().id;
        var delayedOrder = orders.create(merchantId, new OrderDtos.Create(9000, "delayed-" + System.nanoTime()));
        var delayed = payments.create(merchantId, "delayed-" + System.nanoTime(),
                new PaymentDtos.Create(delayedOrder.id, PaymentDtos.Method.UPI, PaymentDtos.Scenario.DELAYED_SUCCESS));
        assertEquals("PENDING", payments.process(merchantId, delayed.id).status);
        delayed = paymentRepository.findById(delayed.id).orElseThrow();
        delayed.resolveAt = Instant.now().minusSeconds(1);
        paymentRepository.save(delayed);
        assertEquals("SUCCESS", payments.reconcile(merchantId, delayed.id).status);

        var timeoutOrder = orders.create(merchantId, new OrderDtos.Create(9000, "timeout-" + System.nanoTime()));
        var timeout = payments.create(merchantId, "timeout-" + System.nanoTime(),
                new PaymentDtos.Create(timeoutOrder.id, PaymentDtos.Method.UPI, PaymentDtos.Scenario.TIMEOUT));
        assertEquals("PENDING", payments.process(merchantId, timeout.id).status);
        timeout = paymentRepository.findById(timeout.id).orElseThrow();
        timeout.resolveAt = Instant.now().minusSeconds(1);
        paymentRepository.save(timeout);
        assertEquals("FAILED", payments.reconcile(merchantId, timeout.id).status);
        assertEquals("CREATED", orders.get(merchantId, timeoutOrder.id).status);
    }

    @Test void refundAndSettlementRetriesDoNotDuplicateLedgerChanges() {
        var registered = merchants.register(new MerchantDtos.Register("Refund Test", "refund-test@example.com"));
        String merchantId = registered.merchant().id;
        var order = orders.create(merchantId, new OrderDtos.Create(20000, "refund-" + System.nanoTime()));
        var payment = payments.create(merchantId, "pay-" + System.nanoTime(),
                new PaymentDtos.Create(order.id, PaymentDtos.Method.CARD, PaymentDtos.Scenario.SUCCESS));
        payment = payments.process(merchantId, payment.id);
        assertEquals("SUCCESS", payment.status);

        String refundKey = "refund-" + System.nanoTime();
        var refundRequest = new RefundDtos.Create(payment.id, 5000);
        var refund = refunds.create(merchantId, refundKey, refundRequest);
        var refundRetry = refunds.create(merchantId, refundKey, refundRequest);
        assertEquals(refund.id, refundRetry.id);
        assertEquals(5000, payments.get(merchantId, payment.id).refunded);

        String settlementKey = "settle-" + System.nanoTime();
        var settlement = settlements.create(merchantId, settlementKey);
        var settlementRetry = settlements.create(merchantId, settlementKey);
        assertEquals(settlement.id, settlementRetry.id);
        assertEquals(0, merchantRepository.findById(merchantId).orElseThrow().balance);
    }

    @Test void concurrentPaymentRetriesWithOneKeyProduceOnePayment() throws Exception {
        var registered = merchants.register(new MerchantDtos.Register("Concurrent Test", "concurrent-test@example.com"));
        String merchantId = registered.merchant().id;
        var order = orders.create(merchantId, new OrderDtos.Create(7000, "concurrent-" + System.nanoTime()));
        String key = "concurrent-" + System.nanoTime();
        var request = new PaymentDtos.Create(order.id, PaymentDtos.Method.UPI, PaymentDtos.Scenario.SUCCESS);
        var one = java.util.concurrent.CompletableFuture.supplyAsync(() -> payments.create(merchantId, key, request));
        var two = java.util.concurrent.CompletableFuture.supplyAsync(() -> payments.create(merchantId, key, request));
        assertEquals(one.get().id, two.get().id);
    }

    @Test void paymentIdempotencyKeyRejectsDifferentRequest() {
        var registered = merchants.register(new MerchantDtos.Register("Idempotency Test", "idempotency-test@example.com"));
        String merchantId = registered.merchant().id;
        var order = orders.create(merchantId, new OrderDtos.Create(5000, "idempotency-" + System.nanoTime()));
        String key = "integration-" + System.nanoTime();
        var request = new PaymentDtos.Create(order.id, PaymentDtos.Method.UPI, PaymentDtos.Scenario.SUCCESS);
        var original = payments.create(merchantId, key, request);
        var retry = payments.create(merchantId, key, request);
        assertEquals(original.id, retry.id);
        var conflict = assertThrows(ApiException.class, () -> payments.create(merchantId, key,
                new PaymentDtos.Create(order.id, PaymentDtos.Method.UPI, PaymentDtos.Scenario.FAILURE)));
        assertEquals(HttpStatus.CONFLICT, conflict.status);
    }
}
