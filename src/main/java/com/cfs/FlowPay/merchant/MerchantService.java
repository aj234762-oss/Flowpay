package com.cfs.FlowPay.merchant;


import com.cfs.FlowPay.common.ApiException;
import com.cfs.FlowPay.auth.DashboardSessionRepository;
import com.cfs.FlowPay.auth.DashboardUser;
import com.cfs.FlowPay.auth.DashboardUserRepository;
import com.cfs.FlowPay.common.Ids;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

@Service
public class MerchantService {

    private final MerchantRepository repo;

    private final Set<String> allowedHosts;
    private final DashboardUserRepository dashboardUsers;
    private final DashboardSessionRepository dashboardSessions;

    public MerchantService(MerchantRepository repo, @Value("${app.webhook-hosts}") String hosts,
                           DashboardUserRepository dashboardUsers,
                           DashboardSessionRepository dashboardSessions) {
        this.repo = repo;
        this.allowedHosts = Set.of(hosts.split(","));
        this.dashboardUsers = dashboardUsers;
        this.dashboardSessions = dashboardSessions;
    }

    public static String hash(String value)
    {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw  new IllegalStateException(e);
        }
    }

    @Transactional
    public MerchantDtos.Registered register(MerchantDtos.Register r)
    {
        Merchant m = new Merchant();
        m.id= Ids.next("mer");
        m.name=r.name();
        m.email=r.email();

        String key = Ids.next("fp_test");
        m.apiKeyHash=hash(key);
        m.webhookSecret=Ids.next("whsec");
        repo.save(m);
        return new MerchantDtos.Registered(m,key,m.webhookSecret);
    }


    public Merchant authenticate(String key)
    {
        if(key==null || key.isBlank())
        {
            throw new ApiException(HttpStatus.UNAUTHORIZED,"A valid API key or dashboard session is required");
        }
        String tokenHash = hash(key);
        Merchant apiMerchant = repo.findByApiKeyHash(tokenHash).orElse(null);
        if (apiMerchant != null) {
            apiMerchant.role = "OWNER";
            return apiMerchant;
        }
        var session = dashboardSessions.findByTokenHashAndExpiresAtAfter(tokenHash, java.time.Instant.now())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,"Invalid or expired dashboard session"));
        DashboardUser user = dashboardUsers.findById(session.userId)
                .filter(found -> found.merchantId.equals(session.merchantId))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,"Dashboard account is unavailable"));
        Merchant merchant = repo.findById(session.merchantId).orElseThrow(ApiException::missing);
        merchant.role = user.role;
        return merchant;
    }

    public Merchant requireMutation(String token) {
        Merchant merchant = authenticate(token);
        if ("VIEWER".equals(merchant.role)) {
            throw new ApiException(HttpStatus.FORBIDDEN,"Viewer accounts have read-only access");
        }
        return merchant;
    }

    public Merchant requireOwner(String token) {
        Merchant merchant = authenticate(token);
        if (!"OWNER".equals(merchant.role)) {
            throw new ApiException(HttpStatus.FORBIDDEN,"Only merchant owners can manage team access");
        }
        return merchant;
    }

    @Transactional
    public String rotateApiKey(String merchantId) {
        Merchant merchant = lock(merchantId);
        String apiKey = Ids.next("fp_test");
        merchant.apiKeyHash = hash(apiKey);
        return apiKey;
    }

    @Transactional
    public String rotateWebhookSecret(String merchantId) {
        Merchant merchant = lock(merchantId);
        merchant.webhookSecret = Ids.next("whsec");
        return merchant.webhookSecret;
    }

    public Merchant lock(String id)
    {
        return repo.lockById(id).orElseThrow(ApiException::missing);
    }


    @Transactional
    public Merchant update(String id,MerchantDtos.Update r)
    {
        Merchant m = lock(id);
        validate(r.webhookUrl());
        m.name=r.name();
        m.email=r.email();
        m.webhookUrl=r.webhookUrl();
        return m;
    }

    public void validate(String value)
    {
        if(value==null || value.isBlank()) return;
        try {
            URI u=URI.create(value);
            if(!Set.of("http","https").contains(u.getScheme())
            || !allowedHosts.contains(u.getHost())
            || u.getUserInfo() !=null
            || u.getFragment()!=null)
            {
                throw new IllegalStateException();
            }
        }
        catch (Exception e)
        {
            throw ApiException.bad("Webhook URL must be HTTP(s) and host in Webhook_hosts");
        }
    }
}
