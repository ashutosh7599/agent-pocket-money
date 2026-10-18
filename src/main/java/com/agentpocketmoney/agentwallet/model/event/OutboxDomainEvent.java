package com.agentpocketmoney.agentwallet.model.event;
import java.time.Instant;
import java.util.UUID;
public record OutboxDomainEvent(UUID id,String aggregateType,UUID aggregateId,String eventType,String payload,Instant createdAt) {}
