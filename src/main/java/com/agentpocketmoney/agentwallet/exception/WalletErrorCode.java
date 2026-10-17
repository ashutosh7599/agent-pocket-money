package com.agentpocketmoney.agentwallet.exception;

public enum WalletErrorCode {
    WALLET_NOT_FOUND,
    WALLET_FROZEN,
    WALLET_CLOSED,
    IDEMPOTENCY_KEY_REUSED,
    INVALID_WALLET_REQUEST
}
