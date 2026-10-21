package com.cfs.FlowPay.Checkout;

import com.cfs.FlowPay.Payment.PaymentDtos;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class CheckoutDtos {
    private CheckoutDtos() {}

    public record Create(String orderId) {}
    public record Pay(@NotNull PaymentDtos.Method method, @NotNull PaymentDtos.Scenario scenario) {}
    public record View(String sessionId, String status, String merchantName, String orderId,
                       String receipt, long amount, String currency, Instant expiresAt,
                       String paymentId, String paymentStatus) {}
}
