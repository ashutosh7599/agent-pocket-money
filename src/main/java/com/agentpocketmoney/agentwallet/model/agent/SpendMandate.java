package com.agentpocketmoney.agentwallet.model.agent;

import com.agentpocketmoney.agentwallet.model.wallet.Wallet;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name="spend_mandates")
public class SpendMandate {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="wallet_id",nullable=false,updatable=false) private Wallet wallet;
    @Column(nullable=false,length=64,updatable=false) private String purpose;
    @Column(name="merchant_id",nullable=false,length=64,updatable=false) private String merchantId;
    @Column(name="max_amount_minor_units",nullable=false,updatable=false) private long maxAmountMinorUnits;
    @Column(nullable=false,length=3,updatable=false) private String currency;
    @Column(nullable=false,length=32,updatable=false) private String category;
    @Column(name="expires_at",nullable=false,updatable=false) private Instant expiresAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private SpendMandateStatus status;
    @Column(name="consumed_payment_id",unique=true) private UUID consumedPaymentId;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected SpendMandate(){}
    public SpendMandate(UUID id,Wallet wallet,String purpose,String merchantId,long maxAmount,String currency,
            String category,Instant expiresAt,Instant now){
        this.id=id;this.wallet=wallet;this.purpose=purpose;this.merchantId=merchantId;this.maxAmountMinorUnits=maxAmount;
        this.currency=currency;this.category=category;this.expiresAt=expiresAt.truncatedTo(ChronoUnit.MICROS);
        this.status=SpendMandateStatus.ACTIVE;this.createdAt=now.truncatedTo(ChronoUnit.MICROS);
    }
    public boolean consume(UUID paymentId){if(status!=SpendMandateStatus.ACTIVE)return false;status=SpendMandateStatus.CONSUMED;consumedPaymentId=paymentId;return true;}
    public UUID getId(){return id;} public Wallet getWallet(){return wallet;} public String getPurpose(){return purpose;}
    public String getMerchantId(){return merchantId;} public long getMaxAmountMinorUnits(){return maxAmountMinorUnits;}
    public String getCurrency(){return currency;} public String getCategory(){return category;} public Instant getExpiresAt(){return expiresAt;}
    public SpendMandateStatus getStatus(){return status;} public UUID getConsumedPaymentId(){return consumedPaymentId;} public Instant getCreatedAt(){return createdAt;}
}
