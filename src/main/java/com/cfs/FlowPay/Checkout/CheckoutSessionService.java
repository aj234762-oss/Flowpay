package com.cfs.FlowPay.Checkout;

import com.cfs.FlowPay.Common.ApiException;
import com.cfs.FlowPay.Common.Ids;
import com.cfs.FlowPay.Merchant.Merchant;
import com.cfs.FlowPay.Merchant.MerchantService;
import com.cfs.FlowPay.Order.OrderService;
import com.cfs.FlowPay.Order.PaymentOrder;
import com.cfs.FlowPay.Payment.Payment;
import com.cfs.FlowPay.Payment.PaymentDtos;
import com.cfs.FlowPay.Payment.PaymentService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CheckoutSessionService {
    private final CheckoutSessionRepository sessions;
    private final MerchantService merchants;
    private final OrderService orders;
    private final PaymentService payments;
    private final String publicBaseUrl;

    public CheckoutSessionService(CheckoutSessionRepository sessions, MerchantService merchants,
                                  OrderService orders, PaymentService payments,
                                  @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.sessions = sessions;
        this.merchants = merchants;
        this.orders = orders;
        this.payments = payments;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    @Transactional
    public Created create(String merchantId, String orderId) {
        merchants.lock(merchantId);
        PaymentOrder order = orders.get(merchantId, orderId);
        if (!"CREATED".equals(order.status)) {
            throw ApiException.conflict("Checkout can only be opened for an unpaid order");
        }
        CheckoutSession session = new CheckoutSession();
        session.id = Ids.next("cs");
        session.token = Ids.next("checkout");
        session.merchantId = merchantId;
        session.orderId = order.id;
        session.expiresAt = Instant.now().plusSeconds(30 * 60);
        sessions.save(session);
        return new Created(session, publicBaseUrl + "/checkout/" + session.token);
    }

    @Transactional
    public CheckoutDtos.View view(String token) {
        CheckoutSession session = sessions.lockByToken(token).orElseThrow(ApiException::missing);
        PaymentOrder order = orders.get(session.merchantId, session.orderId);
        Merchant merchant = merchants.lock(session.merchantId);
        Payment payment = session.paymentId == null ? null : payments.get(session.merchantId, session.paymentId);
        if ("ACTIVE".equals(session.status) && Instant.now().isAfter(session.expiresAt)) session.status = "EXPIRED";
        if (payment != null && ("SUCCESS".equals(payment.status) || "FAILED".equals(payment.status))) {
            session.status = "COMPLETED";
        }
        return new CheckoutDtos.View(session.id, session.status, merchant.name, order.id,
                order.receipt, order.amount, order.currency, session.expiresAt,
                session.paymentId, payment == null ? null : payment.status);
    }

    @Transactional
    public CheckoutDtos.View pay(String token, CheckoutDtos.Pay request) {
        CheckoutSession session = sessions.lockByToken(token).orElseThrow(ApiException::missing);
        if (Instant.now().isAfter(session.expiresAt)) session.status = "EXPIRED";
        if (!"ACTIVE".equals(session.status)) {
            if (session.paymentId != null) return view(token);
            throw ApiException.conflict("Checkout session is no longer active");
        }
        String key = "checkout-" + session.id;
        Payment payment = payments.create(session.merchantId, key,
                new PaymentDtos.Create(session.orderId, request.method(), request.scenario()));
        session.paymentId = payment.id;
        session.status = "PROCESSING";
        payment = payments.process(session.merchantId, payment.id);
        if ("SUCCESS".equals(payment.status) || "FAILED".equals(payment.status)) session.status = "COMPLETED";
        return view(token);
    }

    public record Created(CheckoutSession session, String checkoutUrl) {}
}
