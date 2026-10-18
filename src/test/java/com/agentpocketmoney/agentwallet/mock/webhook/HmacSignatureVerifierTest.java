package com.agentpocketmoney.agentwallet.mock.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HmacSignatureVerifierTest {
    @Test
    void verifiesSignatureAgainstExactRawBody() {
        HmacSignatureVerifier verifier = new HmacSignatureVerifier("test-secret");
        String body = "{\"eventId\":\"abc\"}";

        assertThat(verifier.verify(body, verifier.sign(body))).isTrue();
        assertThat(verifier.verify(body + " ", verifier.sign(body))).isFalse();
        assertThat(verifier.verify(body, "sha256=invalid")).isFalse();
    }

    @Test
    void rejectsSignaturesWhenSecretIsNotConfigured() {
        HmacSignatureVerifier verifier = new HmacSignatureVerifier("");

        assertThat(verifier.verify("{}", "sha256=00")).isFalse();
    }
}
