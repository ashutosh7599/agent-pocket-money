package com.agentpocketmoney.agentwallet.dto;
import com.agentpocketmoney.agentwallet.model.wallet.PaymentRefund;
import java.time.Instant;
import java.util.UUID;
public record RefundResponse(UUID id,UUID paymentId,UUID journalEntryId,long amountMinorUnits,Instant createdAt) {
    public static RefundResponse from(PaymentRefund r){return new RefundResponse(r.getId(),r.getPayment().getId(),r.getJournalEntry().getId(),r.getAmountMinorUnits(),r.getCreatedAt());}
}
