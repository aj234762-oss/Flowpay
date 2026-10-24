package com.cfs.FlowPay.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class DashboardUserDtos {
    private DashboardUserDtos() {}

    public record Create(@NotBlank @Email @Size(max = 160) String email,
                         @NotBlank @Size(min = 8, max = 72) String password,
                         @NotBlank String role) {}

    public record View(String id, String email, String role, Instant createdAt) {}
}
