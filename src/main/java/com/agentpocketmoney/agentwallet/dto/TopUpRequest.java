package com.agentpocketmoney.agentwallet.dto;

import jakarta.validation.constraints.Positive;

public record TopUpRequest(@Positive long amountMinorUnits) {}
