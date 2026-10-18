package com.agentpocketmoney.agentwallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
        @NotBlank @Size(max = 64) String merchantId,
        @Positive long amountMinorUnits,
        @NotBlank @Size(max = 32) String category,
        @NotBlank @Size(max = 64) String purpose) {}
