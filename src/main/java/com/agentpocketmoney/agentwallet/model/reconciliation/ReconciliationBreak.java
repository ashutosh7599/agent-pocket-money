package com.agentpocketmoney.agentwallet.model.reconciliation;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_breaks")
public class ReconciliationBreak {
    @Id private UUID id;
    @Column(name = "batch_id", nullable = false) private UUID batchId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ReconciliationBreakType type;
    @Column(name = "payment_id") private UUID paymentId;
    @Column(name = "report_payment_id") private UUID reportPaymentId;
    @Column(name = "expected_amount_minor_units") private Long expectedAmountMinorUnits;
    @Column(name = "reported_amount_minor_units") private Long reportedAmountMinorUnits;
    @Column(name = "expected_status", length = 24) private String expectedStatus;
    @Column(name = "reported_status", length = 24) private String reportedStatus;
    @Column(nullable = false, length = 256) private String detail;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected ReconciliationBreak() {}
    public ReconciliationBreak(UUID id, UUID batchId, ReconciliationBreakType type, UUID paymentId,
            UUID reportPaymentId, Long expectedAmount, Long reportedAmount, String expectedStatus,
            String reportedStatus, String detail, Instant createdAt) {
        this.id=id; this.batchId=batchId; this.type=type; this.paymentId=paymentId; this.reportPaymentId=reportPaymentId;
        this.expectedAmountMinorUnits=expectedAmount; this.reportedAmountMinorUnits=reportedAmount;
        this.expectedStatus=expectedStatus; this.reportedStatus=reportedStatus; this.detail=detail;
        this.createdAt=createdAt.truncatedTo(ChronoUnit.MICROS);
    }
    public UUID getId(){return id;} public UUID getBatchId(){return batchId;} public ReconciliationBreakType getType(){return type;}
    public UUID getPaymentId(){return paymentId;} public UUID getReportPaymentId(){return reportPaymentId;}
    public Long getExpectedAmountMinorUnits(){return expectedAmountMinorUnits;} public Long getReportedAmountMinorUnits(){return reportedAmountMinorUnits;}
    public String getExpectedStatus(){return expectedStatus;} public String getReportedStatus(){return reportedStatus;}
    public String getDetail(){return detail;} public Instant getCreatedAt(){return createdAt;}
}
