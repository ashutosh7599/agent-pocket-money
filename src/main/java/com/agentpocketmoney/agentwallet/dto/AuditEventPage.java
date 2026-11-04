package com.agentpocketmoney.agentwallet.dto;

import java.util.List;

public record AuditEventPage(List<AuditEventResponse> items,int page,int size,long totalElements,int totalPages) {}
