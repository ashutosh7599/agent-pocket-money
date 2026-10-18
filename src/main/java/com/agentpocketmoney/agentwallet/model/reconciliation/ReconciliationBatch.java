package com.agentpocketmoney.agentwallet.model.reconciliation;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_batches")
public class ReconciliationBatch {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ReconciliationStatus status;
    @Column(name = "report_row_count", nullable = false) private int reportRowCount;
    @Column(name = "matched_count", nullable = false) private int matchedCount;
    @Column(name = "break_count", nullable = false) private int breakCount;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected ReconciliationBatch() {}
    public ReconciliationBatch(UUID id, int reportRowCount, int matchedCount, int breakCount, Instant createdAt) {
        this.id=id; this.reportRowCount=reportRowCount; this.matchedCount=matchedCount; this.breakCount=breakCount;
        this.status=breakCount == 0 ? ReconciliationStatus.MATCHED : ReconciliationStatus.BREAKS_FOUND;
        this.createdAt=createdAt.truncatedTo(ChronoUnit.MICROS);
    }
    public UUID getId(){return id;} public ReconciliationStatus getStatus(){return status;}
    public int getReportRowCount(){return reportRowCount;} public int getMatchedCount(){return matchedCount;}
    public int getBreakCount(){return breakCount;} public Instant getCreatedAt(){return createdAt;}
}
