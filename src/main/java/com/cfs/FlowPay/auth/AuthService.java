package com.cfs.FlowPay.auth;

import com.cfs.FlowPay.common.ApiException;
import com.cfs.FlowPay.common.Ids;
import com.cfs.FlowPay.merchant.Merchant;
import com.cfs.FlowPay.merchant.MerchantDtos;
import com.cfs.FlowPay.merchant.MerchantRepository;
import com.cfs.FlowPay.merchant.MerchantService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {
    private static final long SESSION_HOURS = 12;
    private final DashboardUserRepository users;
    private final DashboardSessionRepository sessions;
    private final MerchantRepository merchants;
    private final MerchantService merchantService;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);

    public AuthService(DashboardUserRepository users, DashboardSessionRepository sessions,
                       MerchantRepository merchants, MerchantService merchantService) {
        this.users = users;
        this.sessions = sessions;
        this.merchants = merchants;
        this.merchantService = merchantService;
    }

    @Transactional
    public AuthDtos.Registered register(AuthDtos.Register request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        MerchantDtos.Registered registered = merchantService.register(
                new MerchantDtos.Register(request.name(), request.email()));
        DashboardUser user = new DashboardUser();
        user.id = Ids.next("usr");
        user.merchantId = registered.merchant().id;
        user.email = request.email().trim().toLowerCase();
        user.passwordHash = passwords.encode(request.password());
        user.role = "OWNER";
        users.save(user);
        return new AuthDtos.Registered(registered.merchant(), registered.apiKey(),
                registered.webhookSecret(), createSession(user), user.role);
    }

    @Transactional
    public AuthDtos.Authenticated login(AuthDtos.Login request) {
        DashboardUser user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));
        if (!passwords.matches(request.password(), user.passwordHash)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
        }
        Merchant merchant = merchants.findById(user.merchantId).orElseThrow(ApiException::missing);
        merchant.role = user.role;
        return new AuthDtos.Authenticated(createSession(user), user.role, merchant);
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.deleteByTokenHash(MerchantService.hash(token));
        }
    }

    @Transactional
    public AuthDtos.CredentialRotation rotateApiKey(String token, String password) {
        DashboardUser owner = authenticateOwner(token, password);
        return new AuthDtos.CredentialRotation(merchantService.rotateApiKey(owner.merchantId));
    }

    @Transactional
    public AuthDtos.CredentialRotation rotateWebhookSecret(String token, String password) {
        DashboardUser owner = authenticateOwner(token, password);
        return new AuthDtos.CredentialRotation(merchantService.rotateWebhookSecret(owner.merchantId));
    }

    private DashboardUser authenticateOwner(String token, String password) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in with your dashboard account first");
        }
        DashboardSession session = sessions
                .findByTokenHashAndExpiresAtAfter(MerchantService.hash(token), Instant.now())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in again to continue"));
        DashboardUser user = users.findById(session.userId)
                .filter(found -> found.merchantId.equals(session.merchantId))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Dashboard account is unavailable"));
        if (!"OWNER".equals(user.role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only merchant owners can rotate credentials");
        }
        if (!passwords.matches(password, user.passwordHash)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Password is incorrect");
        }
        return user;
    }

    private String createSession(DashboardUser user) {
        String token = Ids.next("fpsess");
        DashboardSession session = new DashboardSession();
        session.id = Ids.next("sess");
        session.tokenHash = MerchantService.hash(token);
        session.userId = user.id;
        session.merchantId = user.merchantId;
        session.expiresAt = Instant.now().plusSeconds(SESSION_HOURS * 60 * 60);
        sessions.save(session);
        return token;
    }
}
