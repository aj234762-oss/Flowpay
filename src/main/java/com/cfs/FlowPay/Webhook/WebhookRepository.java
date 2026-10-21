package com.cfs.FlowPay.Webhook;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface WebhookRepository extends JpaRepository<Webhook, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Webhook e where e.id=:id")
    Optional<Webhook> lockById(@Param("id") String id);
    List<Webhook> findByMerchantIdOrderByCreateAtDesc(String merchantId);
    List<Webhook> findByStatusAndNextAttemptLessThanEqualOrderByNextAttemptAscCreateAtAsc(String status, java.time.Instant now, Pageable pageable);
}
