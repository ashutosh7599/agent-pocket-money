package com.agentpocketmoney.agentwallet.dto;
import java.util.UUID;
public record WebhookResponse(String result,UUID eventId) {}
