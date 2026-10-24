package com.cfs.FlowPay.checkout;

import com.cfs.FlowPay.payment.PaymentDtos;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class CheckoutDtos {
    private CheckoutDtos() {}

    public record Pay(@NotNull PaymentDtos.Method method, @NotNull PaymentDtos.Scenario scenario) {}

    public record View(String sessionId, String status, String merchantName, String orderId,
                       String receipt, long amount, String currency, Instant expiresAt,
                       String paymentId, String paymentStatus) {}
}
