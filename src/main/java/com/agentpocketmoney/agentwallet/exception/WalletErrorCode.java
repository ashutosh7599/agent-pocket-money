package com.agentpocketmoney.agentwallet.exception;

public enum WalletErrorCode {
    WALLET_NOT_FOUND,
    HOLD_NOT_FOUND,
    INSUFFICIENT_AVAILABLE_BALANCE,
    WALLET_FROZEN,
    WALLET_CLOSED,
    IDEMPOTENCY_KEY_REUSED,
    INVALID_WALLET_REQUEST
}
