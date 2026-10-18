package com.agentpocketmoney.agentwallet.dto;
import java.util.List;
import java.util.UUID;
public record WalletPolicyResponse(UUID walletId,List<String> allowedMerchantIds,Long maxTransactionAmountMinorUnits,
 Long dailyLimitMinorUnits,Long monthlyLimitMinorUnits,Long manualApprovalThresholdMinorUnits,
 Integer velocityMaxTransactions,Long velocityWindowSeconds,List<CategoryBudgetRequest> categoryBudgets) {}
