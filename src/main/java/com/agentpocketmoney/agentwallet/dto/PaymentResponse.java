package com.agentpocketmoney.agentwallet.dto;

import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import com.agentpocketmoney.agentwallet.model.wallet.PaymentStatus;
import com.agentpocketmoney.agentwallet.model.wallet.GatewayScenario;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(UUID id, UUID walletId, String merchantId, long amountMinorUnits,
        String currency, String category, String purpose, PaymentStatus status, long amountRefundedMinorUnits, String declineReason,
        UUID authorizationHoldId, GatewayScenario gatewayScenario, String gatewayPaymentId,
        String networkTransactionId, String issuerResponseCode, String approvalReason, Instant createdAt, Instant updatedAt) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getWallet().getId(), p.getMerchantId(), p.getAmountMinorUnits(),
                p.getCurrency(), p.getCategory(), p.getPurpose(), p.getStatus(), p.getAmountRefundedMinorUnits(), p.getDeclineReason(),
                p.getAuthorizationHoldId(), p.getGatewayScenario(), p.getGatewayPaymentId(), p.getNetworkTransactionId(),
                p.getIssuerResponseCode(), p.getApprovalReason(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
