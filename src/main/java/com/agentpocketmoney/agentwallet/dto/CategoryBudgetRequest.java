package com.agentpocketmoney.agentwallet.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
public record CategoryBudgetRequest(@NotBlank @Size(max=32) String category,@Positive long limitMinorUnits) {}
