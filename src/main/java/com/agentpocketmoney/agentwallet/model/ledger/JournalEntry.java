package com.agentpocketmoney.agentwallet.model.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
public class JournalEntry {
    @Id
    private UUID id;

    @Column(name = "transaction_reference", nullable = false, length = 128)
    private String transactionReference;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt;

    protected JournalEntry() {}

    public JournalEntry(String transactionReference, String description) {
        this.id = UUID.randomUUID();
        this.transactionReference = transactionReference;
        this.description = description;
        this.postedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public String getTransactionReference() { return transactionReference; }
    public String getDescription() { return description; }
    public Instant getPostedAt() { return postedAt; }
}
