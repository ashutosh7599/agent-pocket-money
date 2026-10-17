package com.agentpocketmoney.agentwallet.dto;

import java.time.Instant;
import java.util.UUID;

import com.agentpocketmoney.agentwallet.model.ledger.PostingSide;

public record WalletTransactionResponse(UUID journalEntryId, String transactionReference,
                                        String description, PostingSide side,
                                        long amountMinorUnits, String currency, Instant postedAt) {}
