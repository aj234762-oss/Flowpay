package com.cfs.FlowPay.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface DashboardSessionRepository extends JpaRepository<DashboardSession, String> {
    Optional<DashboardSession> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
    void deleteByTokenHash(String tokenHash);
}
