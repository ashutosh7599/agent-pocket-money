package com.agentpocketmoney.agentwallet.model.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;

@Entity
@Table(name = "wallets")
public class Wallet {
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_account_id", nullable = false, unique = true, updatable = false)
    private LedgerAccount ledgerAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WalletStatus status;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Wallet() {}

    public Wallet(UUID id, UUID ownerId, LedgerAccount ledgerAccount, String currency) {
        this.id = id;
        this.ownerId = ownerId;
        this.ledgerAccount = ledgerAccount;
        this.currency = currency;
        this.status = WalletStatus.ACTIVE;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Wallet(UUID id, LedgerAccount ledgerAccount, String currency) {
        this(id, UUID.fromString("00000000-0000-0000-0000-000000000001"), ledgerAccount, currency);
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public LedgerAccount getLedgerAccount() { return ledgerAccount; }
    public WalletStatus getStatus() { return status; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}
