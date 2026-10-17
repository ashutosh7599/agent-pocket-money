package com.agentpocketmoney.agentwallet.model.wallet;

import java.time.Instant;
import java.util.UUID;

import com.agentpocketmoney.agentwallet.model.ledger.PostingSide;

public record WalletTransactionRecord(
        UUID journalEntryId,
        String transactionReference,
        String description,
        PostingSide side,
        long amountMinorUnits,
        String currency,
        Instant postedAt) {}
