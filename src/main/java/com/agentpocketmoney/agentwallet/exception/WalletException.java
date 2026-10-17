package com.agentpocketmoney.agentwallet.exception;

public class WalletException extends RuntimeException {
    private final WalletErrorCode errorCode;

    public WalletException(WalletErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public WalletErrorCode getErrorCode() {
        return errorCode;
    }
}
