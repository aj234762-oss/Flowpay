package com.cfs.FlowPay.Settlement;


import com.cfs.FlowPay.Common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "settlements",
        uniqueConstraints = @UniqueConstraint(name = "uk_settlements_merchant_idempotency_key", columnNames = {"merchantId", "idempotencyKey"})
)
public class Settlement extends BaseEntity {

    @Column(nullable = false,length = 48)
    public String merchantId;

    @Column(nullable = false,length = 100)
    public String idempotencyKey;


    @Column(nullable = false)
    public long amount;

    @Column(nullable = false,length = 20)
    public String status = "SETTLED";

}
