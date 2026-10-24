package com.cfs.FlowPay.auth;

import com.cfs.FlowPay.common.ApiException;
import com.cfs.FlowPay.common.Ids;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Locale;

@Service
public class DashboardUserService {
    private final DashboardUserRepository users;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);

    public DashboardUserService(DashboardUserRepository users) {
        this.users = users;
    }

    public List<DashboardUserDtos.View> list(String merchantId) {
        return users.findByMerchantIdOrderByCreatedAtAsc(merchantId).stream()
                .map(user -> new DashboardUserDtos.View(user.id, user.email, user.role, user.createdAt))
                .toList();
    }

    @Transactional
    public DashboardUserDtos.View create(String merchantId, DashboardUserDtos.Create request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        String role = request.role().trim().toUpperCase(Locale.ROOT);
        if (!List.of("OWNER", "VIEWER").contains(role)) {
            throw ApiException.bad("Role must be OWNER or VIEWER");
        }
        DashboardUser user = new DashboardUser();
        user.id = Ids.next("usr");
        user.merchantId = merchantId;
        user.email = email;
        user.passwordHash = passwords.encode(request.password());
        user.role = role;
        users.save(user);
        return new DashboardUserDtos.View(user.id, user.email, user.role, user.createdAt);
    }
}
