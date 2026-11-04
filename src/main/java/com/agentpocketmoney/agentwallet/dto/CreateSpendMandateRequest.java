package com.agentpocketmoney.agentwallet.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateSpendMandateRequest(@NotBlank @Size(max=64) String purpose,
        @NotBlank @Size(max=64) String merchantId,@Positive long maxAmountMinorUnits,
        @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,@NotBlank @Size(max=32) String category,
        @NotNull Instant expiresAt) {}
