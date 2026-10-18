package com.agentpocketmoney.agentwallet.security;

import java.util.UUID;

public record ApiKeyPrincipal(String role, UUID walletScope, String keyId) {
    @Override public String toString() { return role + "#" + keyId; }
}
