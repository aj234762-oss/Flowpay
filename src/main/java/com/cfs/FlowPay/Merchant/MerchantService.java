package com.cfs.FlowPay.Merchant;

import com.cfs.FlowPay.Common.ApiException;
import com.cfs.FlowPay.Common.Ids;
import jakarta.transaction.Transactional;
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

    public MerchantService(MerchantRepository repo,@Value("${app.webhook-hosts}") String hosts) {
        this.repo = repo;
        this.allowedHosts = Set.of(hosts.split(","));
    }

    public static String hash(String value){
        try{
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public MerchantDtos.Registered register(MerchantDtos.Register r)
    {
        Merchant m = new Merchant();
        m.id = Ids.next("mer");
        m.name = r.name();
        m.email = r.email();

        String key = Ids.next("fp_test");
        m.apikeyHash=hash(key);
        m.webhookSecret=Ids.next("whsec");
        m.webhookUrl="";
        repo.save(m);
        return new MerchantDtos.Registered(m,key,m.webhookSecret);
    }

    public Merchant authenticate(String key)
    {
        if(key==null || key.isBlank())
        {
            throw new ApiException(HttpStatus.UNAUTHORIZED,"A Valid X-Api-key is required");
        }
        return repo.findByApikeyHash(hash(key))
                .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Invalid API key"));
    }

    public Merchant lock(String id){ // to give lock inside another method
        return repo.lockById(id).orElseThrow(ApiException::missing);
    }

    @Transactional
    public Merchant update(String id,MerchantDtos.Update r)
    {
        Merchant m = lock(id);
        validate(r.webhookUrl());
        m.name=r.name();
        m.email=r.email();
        m.webhookUrl=r.webhookUrl()==null ? "" : r.webhookUrl();
        return m;
    }

    public void validate(String value)
    {
        if(value==null || value.isBlank()) return;
        try{
            URI u = URI.create(value);
            if(!Set.of("http","https").contains(u.getScheme())
            || !allowedHosts.contains(u.getHost())
            || u.getUserInfo() != null
            || u.getFragment()!=null)
            {
                throw new IllegalStateException();
            }
        } catch (Exception e) {
            throw ApiException.bad("Webhook URL must be HTTP(s) and host in Webhook_hosts");
        }
    }
};
