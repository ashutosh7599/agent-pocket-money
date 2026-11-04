package com.agentpocketmoney.agentwallet.dto;

import java.util.UUID;

public record SettlementReportRow(UUID paymentId, long amountMinorUnits, String status) {}
