package com.agentpocketmoney.agentwallet.model.ledger;

public enum AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE;

    public PostingSide normalBalanceSide() {
        return switch (this) {
            case ASSET, EXPENSE -> PostingSide.DEBIT;
            case LIABILITY, EQUITY, REVENUE -> PostingSide.CREDIT;
        };
    }
}
