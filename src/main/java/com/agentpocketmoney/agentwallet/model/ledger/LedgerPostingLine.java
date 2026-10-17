package com.agentpocketmoney.agentwallet.model.ledger;

import java.util.UUID;

public record LedgerPostingLine(UUID accountId, PostingSide side, long amountMinorUnits) {}
