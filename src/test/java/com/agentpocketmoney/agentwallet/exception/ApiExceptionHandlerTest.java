package com.agentpocketmoney.agentwallet.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;


class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsIdempotencyConflictToSafeConflictProblem() {
        ProblemDetail problem = handler.handleWalletException(new WalletException(
                WalletErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency key was already used for a different request."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("errorCode", "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void doesNotReturnUnexpectedExceptionDetailsToClient() {
        ProblemDetail problem = handler.handleUnexpectedException(new IllegalStateException("sensitive internal detail"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).doesNotContain("sensitive internal detail");
    }
}
