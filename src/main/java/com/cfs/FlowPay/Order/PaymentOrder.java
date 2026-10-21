package com.cfs.FlowPay.Order;

import com.cfs.FlowPay.Common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Orders")
public class PaymentOrder extends BaseEntity {

    @Column(nullable = false,length = 48)
    public String merchantId;

    @Column(nullable = false)
    public long amount;

    @Column(nullable = false,length = 3)
    public String currency = "INR";


    @Column(nullable = false,length = 100)
    public String receipt = "INR";

    @Column(nullable = false,length = 20)
    public String status = "CREATED";

}
