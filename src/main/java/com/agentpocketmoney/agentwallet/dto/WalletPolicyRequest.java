package com.agentpocketmoney.agentwallet.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
public record WalletPolicyRequest(
 @NotEmpty List<@Size(max=64) String> allowedMerchantIds,
 @PositiveOrZero Long maxTransactionAmountMinorUnits,
 @PositiveOrZero Long dailyLimitMinorUnits,
 @PositiveOrZero Long monthlyLimitMinorUnits,
 @PositiveOrZero Long manualApprovalThresholdMinorUnits,
 @Positive Integer velocityMaxTransactions,
 @Positive Long velocityWindowSeconds,
 List<@Valid CategoryBudgetRequest> categoryBudgets) {}
