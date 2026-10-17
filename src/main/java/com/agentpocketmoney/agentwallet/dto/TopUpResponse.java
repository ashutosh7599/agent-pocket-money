package com.agentpocketmoney.agentwallet.dto;

import java.time.Instant;
import java.util.UUID;

public record TopUpResponse(UUID id, UUID walletId, UUID journalEntryId, String transactionReference,
                            long amountMinorUnits, String currency, Instant createdAt) {}
