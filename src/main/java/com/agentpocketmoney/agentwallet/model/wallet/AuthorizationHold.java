package com.agentpocketmoney.agentwallet.model.wallet;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
@Entity @Table(name = "authorization_holds")
public class AuthorizationHold {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "wallet_id", nullable = false, updatable = false) private Wallet wallet;
    @Column(name = "amount_minor_units", nullable = false, updatable = false) private long amountMinorUnits;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private AuthorizationHoldStatus status;
    @Column(name = "expires_at", nullable = false, updatable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected AuthorizationHold() {}
    public AuthorizationHold(UUID id, Wallet wallet, long amountMinorUnits, Instant expiresAt, Instant now) {
        if (amountMinorUnits <= 0) throw new IllegalArgumentException("Hold amount must be positive.");
        if (expiresAt == null || !expiresAt.isAfter(now)) throw new IllegalArgumentException("Hold expiry must be in the future.");
        this.id=id; this.wallet=wallet; this.amountMinorUnits=amountMinorUnits; this.expiresAt=expiresAt;
        this.status=AuthorizationHoldStatus.ACTIVE; this.createdAt=now.truncatedTo(ChronoUnit.MICROS); this.updatedAt=this.createdAt;
    }
    public boolean release(Instant now) { return transition(AuthorizationHoldStatus.RELEASED, now); }
    public boolean expire(Instant now) { return transition(AuthorizationHoldStatus.EXPIRED, now); }
    private boolean transition(AuthorizationHoldStatus target, Instant now) {
        if (status != AuthorizationHoldStatus.ACTIVE) return false;
        status=target; updatedAt=now.truncatedTo(ChronoUnit.MICROS); return true;
    }
    public UUID getId(){return id;} public Wallet getWallet(){return wallet;} public long getAmountMinorUnits(){return amountMinorUnits;}
    public AuthorizationHoldStatus getStatus(){return status;} public Instant getExpiresAt(){return expiresAt;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
