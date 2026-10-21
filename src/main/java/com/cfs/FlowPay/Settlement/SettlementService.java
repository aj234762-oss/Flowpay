package com.cfs.FlowPay.Settlement;

import com.cfs.FlowPay.Common.ApiException;
import com.cfs.FlowPay.Common.Idempotency;
import com.cfs.FlowPay.Common.Ids;
import com.cfs.FlowPay.Merchant.Merchant;
import com.cfs.FlowPay.Merchant.MerchantService;
import com.cfs.FlowPay.Transaction.LedgerService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SettlementService {

    private final SettlementRepository repo;
    private final MerchantService merchants;
    private final LedgerService ledger;
    private final com.cfs.FlowPay.kafka.EventService events;

    public SettlementService(SettlementRepository repo, MerchantService merchants, LedgerService ledger, com.cfs.FlowPay.kafka.EventService events) {
        this.repo = repo;
        this.merchants = merchants;
        this.ledger = ledger;
        this.events = events;
    }

    @Transactional
    public Settlement create(String m,String key)
    {
        Idempotency.validate(key);
        Merchant merchant = merchants.lock(m);
        Settlement old = repo.findByMerchantIdAndIdempotencyKey(m,key).orElse(null);
        if(old!=null) {
            // This endpoint has no request body: merchant + key identifies the same
            // "settle available balance" operation, so retries return its original result.
            if (!old.merchantId.equals(m) || !old.idempotencyKey.equals(key)) {
                throw ApiException.conflict("Idempotency key reused with a different settlement request");
            }
            return old;
        }
        if(merchant.balance<=0) throw ApiException.conflict("No available balance to settle");
        Settlement s = new Settlement();
        s.id = Ids.next("stl");
        s.merchantId=m;
        s.idempotencyKey=key;
        s.amount=merchant.balance;
        repo.save(s);
        ledger.record(merchant,s.id,"SETTLEMENT",-s.amount);
        events.create(m, "settlement.created", s.id, s.amount);
        return s;
    }

    public List<Settlement> list(String s)
    {
        return repo.findByMerchantIdOrderByCreateAtDesc(s);
    }
}
