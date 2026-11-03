package com.agentpocketmoney.agentwallet.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String username, boolean active, Instant createdAt) {}
