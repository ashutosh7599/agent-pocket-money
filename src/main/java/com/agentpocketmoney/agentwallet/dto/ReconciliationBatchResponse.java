package com.agentpocketmoney.agentwallet.dto;

import com.agentpocketmoney.agentwallet.model.reconciliation.ReconciliationBatch;
import com.agentpocketmoney.agentwallet.model.reconciliation.ReconciliationBreak;
import com.agentpocketmoney.agentwallet.model.reconciliation.ReconciliationBreakType;
import com.agentpocketmoney.agentwallet.model.reconciliation.ReconciliationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationBatchResponse(UUID id, ReconciliationStatus status, int reportRowCount,
        int matchedCount, int breakCount, Instant createdAt) {
    public static ReconciliationBatchResponse from(ReconciliationBatch batch) {
        return new ReconciliationBatchResponse(batch.getId(), batch.getStatus(), batch.getReportRowCount(),
                batch.getMatchedCount(), batch.getBreakCount(), batch.getCreatedAt());
    }
    public record Break(UUID id, UUID batchId, ReconciliationBreakType type, UUID paymentId,
            UUID reportPaymentId, Long expectedAmountMinorUnits, Long reportedAmountMinorUnits,
            String expectedStatus, String reportedStatus, String detail, Instant createdAt) {
        public static Break from(ReconciliationBreak value) {
            return new Break(value.getId(), value.getBatchId(), value.getType(), value.getPaymentId(),
                    value.getReportPaymentId(), value.getExpectedAmountMinorUnits(), value.getReportedAmountMinorUnits(),
                    value.getExpectedStatus(), value.getReportedStatus(), value.getDetail(), value.getCreatedAt());
        }
    }
    public record BreakPage(List<Break> breaks) {}
}
