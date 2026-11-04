package com.agentpocketmoney.agentwallet.dto;

import com.agentpocketmoney.agentwallet.model.identity.CredentialRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCredentialRequest(@NotNull CredentialRole role, UUID walletScope) {}
