package com.agentpocketmoney.agentwallet.dto;
import jakarta.validation.constraints.Positive;
public record RefundRequest(@Positive long amountMinorUnits) {}
