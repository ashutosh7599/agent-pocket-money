package com.agentpocketmoney.agentwallet.model.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.agentpocketmoney.agentwallet.model.ledger.JournalEntry;

@Entity
@Table(name = "wallet_top_ups", uniqueConstraints =
        @UniqueConstraint(name = "uq_wallet_top_up_idempotency", columnNames = {"wallet_id", "idempotency_key"}))
public class TopUp {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false, updatable = false)
    private JournalEntry journalEntry;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "amount_minor_units", nullable = false, updatable = false)
    private long amountMinorUnits;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TopUp() {}

    public TopUp(UUID id, Wallet wallet, JournalEntry journalEntry, String idempotencyKey,
                 long amountMinorUnits, String currency) {
        this.id = id;
        this.wallet = wallet;
        this.journalEntry = journalEntry;
        this.idempotencyKey = idempotencyKey;
        this.amountMinorUnits = amountMinorUnits;
        this.currency = currency;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public Wallet getWallet() { return wallet; }
    public JournalEntry getJournalEntry() { return journalEntry; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getAmountMinorUnits() { return amountMinorUnits; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}
