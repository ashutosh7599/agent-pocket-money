package com.agentpocketmoney.agentwallet.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiKeyAuthenticationFilterTest {
    @AfterEach void clearContext(){SecurityContextHolder.clearContext();}
    @Test void authenticatesRequestScopedToConfiguredWallet() throws Exception {
        UUID wallet=UUID.randomUUID();
        ApiKeyAuthenticationFilter filter=new ApiKeyAuthenticationFilter(new ApiKeyRegistry("agent:AGENT:"+wallet));
        MockHttpServletRequest request=new MockHttpServletRequest("GET","/api/v1/wallets/"+wallet);
        request.addHeader("X-API-Key","agent");
        MockHttpServletResponse response=new MockHttpServletResponse();
        MockFilterChain chain=new MockFilterChain();

        filter.doFilter(request,response,chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).contains("ROLE_AGENT");
    }
    @Test void blocksAgentAccessToDifferentWallet() throws Exception {
        UUID wallet=UUID.randomUUID();
        ApiKeyAuthenticationFilter filter=new ApiKeyAuthenticationFilter(new ApiKeyRegistry("agent:AGENT:"+wallet));
        MockHttpServletRequest request=new MockHttpServletRequest("GET","/api/v1/wallets/"+UUID.randomUUID());
        request.addHeader("X-API-Key","agent");
        MockHttpServletResponse response=new MockHttpServletResponse();

        filter.doFilter(request,response,new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("FORBIDDEN");
    }
    @Test void rejectsInvalidKeyWithoutEchoingIt() throws Exception {
        ApiKeyAuthenticationFilter filter=new ApiKeyAuthenticationFilter(new ApiKeyRegistry("admin:ADMIN"));
        MockHttpServletRequest request=new MockHttpServletRequest("GET","/api/v1/wallets/"+UUID.randomUUID());
        request.addHeader("X-API-Key","wrong-secret");
        MockHttpServletResponse response=new MockHttpServletResponse();

        filter.doFilter(request,response,new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED").doesNotContain("wrong-secret");
    }
}
