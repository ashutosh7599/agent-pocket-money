package com.agentpocketmoney.agentwallet.exception;

public class LedgerException extends RuntimeException {
    private final LedgerErrorCode errorCode;

    public LedgerException(LedgerErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public LedgerException(LedgerErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public LedgerErrorCode getErrorCode() {
        return errorCode;
    }
}
