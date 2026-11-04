package com.agentpocketmoney.agentwallet.dto;

import com.agentpocketmoney.agentwallet.model.agent.SpendMandate;
import com.agentpocketmoney.agentwallet.model.agent.SpendMandateStatus;
import java.time.Instant;
import java.util.UUID;

public record SpendMandateResponse(UUID id,UUID walletId,String purpose,String merchantId,long maxAmountMinorUnits,
        String currency,String category,Instant expiresAt,SpendMandateStatus status,UUID consumedPaymentId,Instant createdAt){
    public static SpendMandateResponse from(SpendMandate m){return new SpendMandateResponse(m.getId(),m.getWallet().getId(),
            m.getPurpose(),m.getMerchantId(),m.getMaxAmountMinorUnits(),m.getCurrency(),m.getCategory(),m.getExpiresAt(),
            m.getStatus(),m.getConsumedPaymentId(),m.getCreatedAt());}
}
