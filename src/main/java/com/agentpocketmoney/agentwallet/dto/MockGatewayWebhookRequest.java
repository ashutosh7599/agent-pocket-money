package com.agentpocketmoney.agentwallet.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
public record MockGatewayWebhookRequest(@NotNull UUID eventId,@NotBlank @Size(max=96) String eventType,
 @NotNull UUID paymentId,@NotBlank @Size(max=32) String status,@NotNull Instant occurredAt) {}
