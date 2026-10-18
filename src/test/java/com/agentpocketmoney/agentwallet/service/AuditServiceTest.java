package com.agentpocketmoney.agentwallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentpocketmoney.agentwallet.model.audit.AuditEvent;
import com.agentpocketmoney.agentwallet.repository.AuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditServiceTest {
    private final AuditEventRepository events=org.mockito.Mockito.mock(AuditEventRepository.class);
    private final AuditService service=new AuditService(events,Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"),ZoneOffset.UTC));
    @AfterEach void clearSecurityContext(){SecurityContextHolder.clearContext();}
    @Test void storesActorActionAndEntityWithoutRequestPayload(){
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("AGENT#abcdef012345",null,java.util.List.of()));
        UUID payment=UUID.randomUUID();
        when(events.save(any(AuditEvent.class))).thenAnswer(call->call.getArgument(0));
        service.record("PAYMENT_CAPTURED","payment",payment);
        ArgumentCaptor<AuditEvent> captor=ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).save(captor.capture());
        assertThat(captor.getValue().getActor()).isEqualTo("AGENT#abcdef012345");
        assertThat(captor.getValue().getEntityId()).isEqualTo(payment);
        assertThat(captor.getValue().getAction()).isEqualTo("PAYMENT_CAPTURED");
    }
}
