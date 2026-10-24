package com.cfs.FlowPay.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.Registered register(@Valid @RequestBody AuthDtos.Register request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.Authenticated login(@Valid @RequestBody AuthDtos.Login request) {
        return auth.login(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "X-Api-Key", required = false) String token) {
        auth.logout(token);
    }

    @PostMapping("/credentials/api-key/rotate")
    public AuthDtos.CredentialRotation rotateApiKey(
            @RequestHeader(value = "X-Api-Key", required = false) String token,
            @Valid @RequestBody AuthDtos.PasswordConfirm request) {
        return auth.rotateApiKey(token, request.password());
    }

    @PostMapping("/credentials/webhook-secret/rotate")
    public AuthDtos.CredentialRotation rotateWebhookSecret(
            @RequestHeader(value = "X-Api-Key", required = false) String token,
            @Valid @RequestBody AuthDtos.PasswordConfirm request) {
        return auth.rotateWebhookSecret(token, request.password());
    }
}
