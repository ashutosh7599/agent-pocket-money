package com.agentpocketmoney.agentwallet.dto;

import java.time.Instant;
import java.util.UUID;

import com.agentpocketmoney.agentwallet.model.wallet.WalletStatus;

public record WalletResponse(UUID id, WalletStatus status, String currency,
                             long balanceMinorUnits, Instant createdAt) {}
