package com.agentpocketmoney.agentwallet.dto;

import java.util.List;

public record WalletTransactionPage(List<WalletTransactionResponse> transactions,
                                    int page, int size, long totalElements, int totalPages) {}
