package com.cfs.FlowPay.Transaction;

import com.cfs.FlowPay.Order.PaymentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Optional;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry,String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from LedgerEntry e where e.id=:id")
    Optional<LedgerEntry> lockById(@Param("id") String id);


    List<LedgerEntry> findByMerchantIdOrderByCreateAtDesc(String m);
    Optional<LedgerEntry> findByReferenceIdAndType(String referenceId, String type);
}
