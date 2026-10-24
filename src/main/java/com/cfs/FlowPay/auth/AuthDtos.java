package com.cfs.FlowPay.auth;

import com.cfs.FlowPay.merchant.Merchant;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record Register(@NotBlank @Size(max = 100) String name,
                           @NotBlank @Email @Size(max = 160) String email,
                           @NotBlank @Size(min = 8, max = 72) String password) {}

    public record Login(@NotBlank @Email @Size(max = 160) String email,
                        @NotBlank @Size(max = 72) String password) {}

    public record PasswordConfirm(@NotBlank @Size(max = 72) String password) {}

    public record CredentialRotation(String value) {}

    public record Authenticated(String sessionToken, String role, Merchant merchant) {}

    public record Registered(Merchant merchant, String apiKey, String webhookSecret,
                             String sessionToken, String role) {}
}
