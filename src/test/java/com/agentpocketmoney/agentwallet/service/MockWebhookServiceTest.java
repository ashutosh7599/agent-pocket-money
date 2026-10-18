package com.agentpocketmoney.agentwallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.mock.webhook.HmacSignatureVerifier;
import com.agentpocketmoney.agentwallet.model.event.ReceivedWebhook;
import com.agentpocketmoney.agentwallet.repository.ReceivedWebhookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockWebhookServiceTest {
    @Mock ReceivedWebhookRepository webhooks;
    private final HmacSignatureVerifier signatures = new HmacSignatureVerifier("test-secret");
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);
    private MockWebhookService service;
    private String body;
    private UUID eventId;

    @BeforeEach
    void setUp() throws Exception {
        service = new MockWebhookService(webhooks, signatures, json, clock);
        eventId = UUID.randomUUID();
        body = json.writeValueAsString(new com.agentpocketmoney.agentwallet.dto.MockGatewayWebhookRequest(
                eventId, "payment.updated", UUID.randomUUID(), "AUTHORIZED", clock.instant()));
    }

    @Test
    void acceptsSignedWebhookAndPersistsItOnce() {
        when(webhooks.findByEventId(eventId)).thenReturn(Optional.empty());

        assertThat(service.receive(body, signatures.sign(body)).outcome())
                .isEqualTo(MockWebhookService.Outcome.ACCEPTED);
        verify(webhooks).save(any(ReceivedWebhook.class));
    }

    @Test
    void duplicateSignedWebhookIsIgnored() {
        when(webhooks.findByEventId(eventId)).thenReturn(Optional.of(org.mockito.Mockito.mock(ReceivedWebhook.class)));

        assertThat(service.receive(body, signatures.sign(body)).outcome())
                .isEqualTo(MockWebhookService.Outcome.DUPLICATE);
        verify(webhooks, never()).save(any());
    }

    @Test
    void invalidSignatureDoesNotCreateOrMaskDuplicate() {
        assertThat(service.receive(body, "sha256=00").outcome())
                .isEqualTo(MockWebhookService.Outcome.INVALID_SIGNATURE);
        verify(webhooks, never()).findByEventId(any());
        verify(webhooks, never()).save(any());
    }

    @Test
    void malformedPayloadMapsToBadRequestError() {
        assertThatThrownBy(() -> service.receive("{bad", null))
                .isInstanceOf(WalletException.class)
                .extracting("errorCode").isEqualTo(WalletErrorCode.INVALID_WALLET_REQUEST);
    }
}
