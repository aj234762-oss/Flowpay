package com.cfs.FlowPay.Payment;

import com.cfs.FlowPay.Order.PaymentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


public interface PaymentRepository extends JpaRepository<Payment,String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Payment e where e.id=:id")
    Optional<Payment> lockById(@Param("id") String id);


    Optional<Payment> findByMerchantIdAndIdempotencyKey(String m,String k);

    List<Payment> findByMerchantIdOrderByCreateAtDesc(String m);

    boolean existsByOrderIdAndStatusIn(String id, Collection<String> Statuses);

    List<Payment> findTop50ByStatusAndScenarioAndResolveAtBeforeOrderByCreateAtAsc(
            String status, String scenario, Instant now
    );


}
