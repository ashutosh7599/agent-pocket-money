package com.agentpocketmoney.agentwallet.model.wallet;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "payments", uniqueConstraints =
        @UniqueConstraint(name = "uq_payment_idempotency", columnNames = {"wallet_id", "idempotency_key"}))
public class Payment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;
    @Column(name = "merchant_id", nullable = false, length = 64, updatable = false)
    private String merchantId;
    @Column(name = "amount_minor_units", nullable = false, updatable = false)
    private long amountMinorUnits;
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;
    @Column(nullable = false, length = 32, updatable = false)
    private String category;
    @Column(nullable = false, length = 64, updatable = false)
    private String purpose;
    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentStatus status;
    @Column(name = "decline_reason", length = 64)
    private String declineReason;
    @Column(name = "authorization_hold_id")
    private UUID authorizationHoldId;
    @Column(name = "merchant_ledger_account_id")
    private UUID merchantLedgerAccountId;
    @Column(name = "amount_refunded_minor_units", nullable = false)
    private long amountRefundedMinorUnits;
    @Column(name = "capture_idempotency_key", length = 128)
    private String captureIdempotencyKey;
    @Column(name = "void_idempotency_key", length = 128)
    private String voidIdempotencyKey;
    @Enumerated(EnumType.STRING) @Column(name="gateway_scenario",nullable=false,length=16)
    private GatewayScenario gatewayScenario;
    @Column(name="gateway_payment_id",length=64) private String gatewayPaymentId;
    @Column(name="network_transaction_id",length=64) private String networkTransactionId;
    @Column(name="issuer_response_code",length=32) private String issuerResponseCode;
    @Column(name="approval_reason",length=64) private String approvalReason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {}

    public Payment(UUID id, Wallet wallet, String merchantId, long amountMinorUnits, String currency,
                   String category, String purpose, String idempotencyKey, Instant now) {
        this.id=id; this.wallet=wallet; this.merchantId=merchantId; this.amountMinorUnits=amountMinorUnits;
        this.currency=currency; this.category=category; this.purpose=purpose; this.idempotencyKey=idempotencyKey;
        this.status=PaymentStatus.CREATED; this.gatewayScenario=GatewayScenario.APPROVE;
        this.createdAt=now.truncatedTo(ChronoUnit.MICROS); this.updatedAt=this.createdAt;
    }
    public Payment(UUID id,Wallet wallet,String merchantId,long amount,String currency,String category,String purpose,
                   String key,GatewayScenario scenario,Instant now) {
        this(id,wallet,merchantId,amount,currency,category,purpose,key,now);
        this.gatewayScenario=scenario==null?GatewayScenario.APPROVE:scenario;
    }
    public void recordGatewayResult(String gatewayId,String networkId,String issuerCode){
        this.gatewayPaymentId=gatewayId;this.networkTransactionId=networkId;this.issuerResponseCode=issuerCode;
    }

    public boolean authorize(UUID holdId, Instant now) {
        if (status != PaymentStatus.CREATED) return false;
        status=PaymentStatus.AUTHORIZED; authorizationHoldId=holdId; updatedAt=now.truncatedTo(ChronoUnit.MICROS);
        return true;
    }
    public boolean requestApproval(UUID holdId, String reason, Instant now) {
        if (status != PaymentStatus.CREATED) return false;
        status=PaymentStatus.PENDING_APPROVAL; authorizationHoldId=holdId; approvalReason=reason;
        updatedAt=now.truncatedTo(ChronoUnit.MICROS); return true;
    }
    public boolean fail(String reason,Instant now) {
        if(status!=PaymentStatus.CREATED) return false;
        status=PaymentStatus.AUTHORIZATION_FAILED;declineReason=reason;updatedAt=now.truncatedTo(ChronoUnit.MICROS);return true;
    }
    public boolean decline(String reason, Instant now) {
        if (status != PaymentStatus.CREATED) return false;
        status=PaymentStatus.DECLINED; declineReason=reason; updatedAt=now.truncatedTo(ChronoUnit.MICROS);
        return true;
    }
    public boolean capture(UUID merchantAccountId, String key, Instant now) {
        if (status != PaymentStatus.AUTHORIZED) return false;
        status=PaymentStatus.CAPTURED; merchantLedgerAccountId=merchantAccountId;
        captureIdempotencyKey=key; updatedAt=now.truncatedTo(ChronoUnit.MICROS); return true;
    }
    public boolean voidPayment(String key, Instant now) {
        if (status != PaymentStatus.AUTHORIZED) return false;
        status=PaymentStatus.VOIDED; voidIdempotencyKey=key;
        updatedAt=now.truncatedTo(ChronoUnit.MICROS); return true;
    }
    public boolean expire(Instant now) {
        if (status != PaymentStatus.AUTHORIZED) return false;
        status=PaymentStatus.EXPIRED; updatedAt=now.truncatedTo(ChronoUnit.MICROS); return true;
    }
    public boolean refund(long amountMinorUnits, Instant now) {
        if (status != PaymentStatus.CAPTURED && status != PaymentStatus.PARTIALLY_REFUNDED) return false;
        long newTotal=Math.addExact(amountRefundedMinorUnits, amountMinorUnits);
        if (newTotal > this.amountMinorUnits) return false;
        amountRefundedMinorUnits=newTotal;
        status=newTotal == this.amountMinorUnits ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        updatedAt=now.truncatedTo(ChronoUnit.MICROS); return true;
    }
    public UUID getId(){return id;} public Wallet getWallet(){return wallet;} public String getMerchantId(){return merchantId;}
    public long getAmountMinorUnits(){return amountMinorUnits;} public String getCurrency(){return currency;}
    public String getCategory(){return category;} public String getPurpose(){return purpose;}
    public String getIdempotencyKey(){return idempotencyKey;} public PaymentStatus getStatus(){return status;}
    public String getDeclineReason(){return declineReason;}
    public GatewayScenario getGatewayScenario(){return gatewayScenario;}
    public String getGatewayPaymentId(){return gatewayPaymentId;}
    public String getNetworkTransactionId(){return networkTransactionId;}
    public String getIssuerResponseCode(){return issuerResponseCode;}
    public String getApprovalReason(){return approvalReason;} public UUID getAuthorizationHoldId(){return authorizationHoldId;}
    public UUID getMerchantLedgerAccountId(){return merchantLedgerAccountId;}
    public long getAmountRefundedMinorUnits(){return amountRefundedMinorUnits;}
    public String getCaptureIdempotencyKey(){return captureIdempotencyKey;}
    public String getVoidIdempotencyKey(){return voidIdempotencyKey;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
