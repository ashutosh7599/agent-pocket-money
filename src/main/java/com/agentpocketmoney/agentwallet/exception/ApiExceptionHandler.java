package com.agentpocketmoney.agentwallet.exception;

import java.net.URI;
import org.springframework.beans.TypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;


import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final URI PROBLEM_TYPE = URI.create("about:blank");

    @ExceptionHandler(LedgerException.class)
    public ProblemDetail handleLedgerException(LedgerException exception) {
        HttpStatus status = exception.getErrorCode() == LedgerErrorCode.LEDGER_ACCOUNT_NOT_FOUND
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        ProblemDetail problem = problem(status, exception.getMessage());
        problem.setProperty("errorCode", exception.getErrorCode().name());
        addTraceId(problem);
        return problem;
    }

    @ExceptionHandler(WalletException.class)
    public ProblemDetail handleWalletException(WalletException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case WALLET_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case WALLET_FROZEN, WALLET_CLOSED, IDEMPOTENCY_KEY_REUSED -> HttpStatus.CONFLICT;
            case INVALID_WALLET_REQUEST -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = problem(status, exception.getMessage());
        problem.setProperty("errorCode", exception.getErrorCode().name());
        addTraceId(problem);
        return problem;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class, MissingServletRequestParameterException.class,
            TypeMismatchException.class})
    public ProblemDetail handleInvalidRequest(Exception exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed.");
        problem.setProperty("errorCode", "INVALID_REQUEST");
        addTraceId(problem);
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataConflict(DataIntegrityViolationException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "The request conflicts with existing data.");
        problem.setProperty("errorCode", "DATA_CONFLICT");
        addTraceId(problem);
        return problem;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNotFound(NoResourceFoundException exception) {
        return problemWithCode(HttpStatus.NOT_FOUND, "The requested resource was not found.", "NOT_FOUND");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleUnsupportedMethod(HttpRequestMethodNotSupportedException exception) {
        return problemWithCode(HttpStatus.METHOD_NOT_ALLOWED,
                "The HTTP method is not supported for this resource.", "METHOD_NOT_ALLOWED");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return problemWithCode(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "The request content type is not supported.", "UNSUPPORTED_MEDIA_TYPE");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception exception) {
        logger.error("Unhandled request failure", exception);
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "The request could not be completed.");
        problem.setProperty("errorCode", "INTERNAL_ERROR");
        addTraceId(problem);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(PROBLEM_TYPE);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }

    private static ProblemDetail problemWithCode(HttpStatus status, String detail, String errorCode) {
        ProblemDetail problem = problem(status, detail);
        problem.setProperty("errorCode", errorCode);
        addTraceId(problem);
        return problem;
    }

    private static void addTraceId(ProblemDetail problem) {
        String traceId = MDC.get("trace_id");
        if (traceId != null && !traceId.isBlank()) problem.setProperty("traceId", traceId);
    }
}
