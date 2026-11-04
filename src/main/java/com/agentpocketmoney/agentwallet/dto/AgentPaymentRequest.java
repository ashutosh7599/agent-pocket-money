package com.agentpocketmoney.agentwallet.dto;

import jakarta.validation.constraints.Positive;

public record AgentPaymentRequest(@Positive long amountMinorUnits) {}
