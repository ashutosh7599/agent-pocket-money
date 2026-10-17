package com.agentpocketmoney.agentwallet.model.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "journal_lines")
public class JournalLine {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false, updatable = false)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private LedgerAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6, updatable = false)
    private PostingSide side;

    @Column(name = "amount_minor_units", nullable = false, updatable = false)
    private long amountMinorUnits;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    protected JournalLine() {}

    public JournalLine(JournalEntry journalEntry, LedgerAccount account, PostingSide side,
                       long amountMinorUnits, String currency) {
        this.id = UUID.randomUUID();
        this.journalEntry = journalEntry;
        this.account = account;
        this.side = side;
        this.amountMinorUnits = amountMinorUnits;
        this.currency = currency;
    }

    public UUID getId() { return id; }
    public JournalEntry getJournalEntry() { return journalEntry; }
    public LedgerAccount getAccount() { return account; }
    public PostingSide getSide() { return side; }
    public long getAmountMinorUnits() { return amountMinorUnits; }
    public String getCurrency() { return currency; }
}
