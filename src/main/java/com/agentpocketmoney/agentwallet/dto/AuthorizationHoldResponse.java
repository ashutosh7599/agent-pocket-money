package com.agentpocketmoney.agentwallet.dto;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHold;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHoldStatus;
import java.time.Instant;
import java.util.UUID;
public record AuthorizationHoldResponse(UUID id,UUID walletId,long amountMinorUnits,AuthorizationHoldStatus status,Instant expiresAt,Instant createdAt,Instant updatedAt) {
 public static AuthorizationHoldResponse from(AuthorizationHold h){return new AuthorizationHoldResponse(h.getId(),h.getWallet().getId(),h.getAmountMinorUnits(),h.getStatus(),h.getExpiresAt(),h.getCreatedAt(),h.getUpdatedAt());}
}
