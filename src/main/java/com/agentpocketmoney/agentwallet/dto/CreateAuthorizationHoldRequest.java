package com.agentpocketmoney.agentwallet.dto;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
public record CreateAuthorizationHoldRequest(@Positive long amountMinorUnits,@NotNull @Future Instant expiresAt) {}
