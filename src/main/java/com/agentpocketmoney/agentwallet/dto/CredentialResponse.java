package com.agentpocketmoney.agentwallet.dto;

import java.time.Instant;
import java.util.UUID;

public record CredentialResponse(UUID id, String role, UUID walletScope, String apiKey,
        String keyId, Instant createdAt) {}
