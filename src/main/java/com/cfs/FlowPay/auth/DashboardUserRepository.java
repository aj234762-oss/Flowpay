package com.cfs.FlowPay.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DashboardUserRepository extends JpaRepository<DashboardUser, String> {
    Optional<DashboardUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<DashboardUser> findByMerchantIdOrderByCreatedAtAsc(String merchantId);
}
