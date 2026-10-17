package com.agentpocketmoney.agentwallet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationLifecycleLogger {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationLifecycleLogger.class);

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.atInfo()
                .addKeyValue("service.name", "agent-wallet")
                .log("Application ready");
    }
}
