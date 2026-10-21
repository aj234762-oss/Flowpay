package com.cfs.FlowPay.Refund;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class RefundDtos {
    public record Create(@NotBlank String paymentId, @Min(1) @Max(1000000000) long amount)
    {

    }
}
