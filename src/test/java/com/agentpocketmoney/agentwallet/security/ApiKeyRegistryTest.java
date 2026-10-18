package com.agentpocketmoney.agentwallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiKeyRegistryTest {
    @Test void authenticatesAdminWithoutExposingTheConfiguredKey(){
        ApiKeyRegistry registry=new ApiKeyRegistry("private-admin-token:ADMIN");
        ApiKeyPrincipal principal=registry.authenticate("private-admin-token").orElseThrow();
        assertThat(principal.role()).isEqualTo("ADMIN");
        assertThat(principal.toString()).doesNotContain("private-admin-token");
        assertThat(registry.authenticate("wrong-token")).isEmpty();
    }
    @Test void enforcesWalletScopeForAgentKey(){
        UUID wallet=UUID.randomUUID();
        ApiKeyPrincipal principal=new ApiKeyRegistry("agent-token:AGENT:"+wallet).authenticate("agent-token").orElseThrow();
        assertThat(principal.walletScope()).isEqualTo(wallet);
    }
    @Test void rejectsUnscopedAgentKey(){
        assertThatThrownBy(()->new ApiKeyRegistry("agent-token:AGENT"))
                .isInstanceOf(IllegalStateException.class);
    }
}
