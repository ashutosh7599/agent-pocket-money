package com.agentpocketmoney.agentwallet.dto;

import com.agentpocketmoney.agentwallet.model.audit.AuditEvent;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(UUID id,String actor,String action,String entityType,UUID entityId,String requestId,Instant createdAt) {
    public static AuditEventResponse from(AuditEvent event){
        return new AuditEventResponse(event.getId(),event.getActor(),event.getAction(),event.getEntityType(),
                event.getEntityId(),event.getRequestId(),event.getCreatedAt());
    }
}
