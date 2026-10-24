package com.cfs.FlowPay.auth;

import com.cfs.FlowPay.merchant.MerchantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class DashboardUserController {
    private final MerchantService merchants;
    private final DashboardUserService users;

    public DashboardUserController(MerchantService merchants, DashboardUserService users) {
        this.merchants = merchants;
        this.users = users;
    }

    @GetMapping
    public List<DashboardUserDtos.View> list(@RequestHeader("X-Api-Key") String token) {
        return users.list(merchants.authenticate(token).id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DashboardUserDtos.View create(@RequestHeader("X-Api-Key") String token,
                                         @Valid @RequestBody DashboardUserDtos.Create request) {
        String merchantId = merchants.requireOwner(token).id;
        return users.create(merchantId, request);
    }
}
