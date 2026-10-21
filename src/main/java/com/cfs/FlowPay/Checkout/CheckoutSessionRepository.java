package com.cfs.FlowPay.Checkout;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, String> {
    Optional<CheckoutSession> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CheckoutSession s where s.token=:token")
    Optional<CheckoutSession> lockByToken(@Param("token") String token);
}
