package com.agentpocketmoney.agentwallet.security;

import java.util.UUID;

public record ApiKeyPrincipal(String role, UUID userId, UUID walletScope, String keyId) {
    public String actorId() { return userId == null ? role.toLowerCase() + "#" + keyId : userId + "/" + role.toLowerCase() + "#" + keyId; }
    @Override public String toString() { return actorId(); }
}
