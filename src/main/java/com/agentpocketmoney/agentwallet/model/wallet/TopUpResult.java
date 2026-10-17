package com.agentpocketmoney.agentwallet.model.wallet;

import java.time.Instant;
import java.util.UUID;

public record TopUpResult(UUID id, UUID walletId, UUID journalEntryId, String transactionReference,
                          long amountMinorUnits, String currency, Instant createdAt) {
    public static TopUpResult from(TopUp topUp) {
        return new TopUpResult(topUp.getId(), topUp.getWallet().getId(),
                topUp.getJournalEntry().getId(), topUp.getJournalEntry().getTransactionReference(),
                topUp.getAmountMinorUnits(), topUp.getCurrency(), topUp.getCreatedAt());
    }
}
