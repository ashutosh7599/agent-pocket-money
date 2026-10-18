package com.agentpocketmoney.agentwallet.model.wallet;

import com.agentpocketmoney.agentwallet.model.ledger.JournalEntry;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name="payment_refunds", uniqueConstraints=
        @UniqueConstraint(name="uq_payment_refund_idempotency",columnNames={"payment_id","idempotency_key"}))
public class PaymentRefund {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="payment_id",nullable=false,updatable=false)
    private Payment payment;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="journal_entry_id",nullable=false,unique=true,updatable=false)
    private JournalEntry journalEntry;
    @Column(name="idempotency_key",nullable=false,length=128,updatable=false)
    private String idempotencyKey;
    @Column(name="amount_minor_units",nullable=false,updatable=false)
    private long amountMinorUnits;
    @Column(name="created_at",nullable=false,updatable=false)
    private Instant createdAt;
    protected PaymentRefund() {}
    public PaymentRefund(UUID id,Payment payment,JournalEntry entry,String key,long amount,Instant now) {
        this.id=id;this.payment=payment;this.journalEntry=entry;this.idempotencyKey=key;
        this.amountMinorUnits=amount;this.createdAt=now.truncatedTo(ChronoUnit.MICROS);
    }
    public UUID getId(){return id;} public Payment getPayment(){return payment;}
    public JournalEntry getJournalEntry(){return journalEntry;} public String getIdempotencyKey(){return idempotencyKey;}
    public long getAmountMinorUnits(){return amountMinorUnits;} public Instant getCreatedAt(){return createdAt;}
}
