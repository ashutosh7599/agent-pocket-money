package com.agentpocketmoney.agentwallet.dto;

import java.util.List;

public record AuthorizationHoldPage(List<AuthorizationHoldResponse> holds,
        int page, int size, long totalElements, int totalPages) {}
