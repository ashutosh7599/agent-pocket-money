package com.agentpocketmoney.agentwallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._@-]{3,128}") String username) {}
