package com.agentpocketmoney.agentwallet.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info=@Info(title="Agent Wallet API",version="v1",
        description="Sandbox wallet, payments, settlement and agent spending API."),
        security=@SecurityRequirement(name="apiKeyAuth"))
@SecurityScheme(name="apiKeyAuth",type=SecuritySchemeType.APIKEY,in=SecuritySchemeIn.HEADER,
        paramName="X-API-Key",description="Use an API key assigned to an ADMIN, AGENT, or WEBHOOK role.")
public class OpenApiConfiguration {}
