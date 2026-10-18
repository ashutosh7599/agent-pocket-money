package com.agentpocketmoney.agentwallet.service;
import com.agentpocketmoney.agentwallet.model.event.OutboxDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
@Component
public class OutboxEventLogListener {
 private static final Logger logger=LoggerFactory.getLogger(OutboxEventLogListener.class);
 @EventListener public void onEvent(OutboxDomainEvent event){
  logger.atInfo().addKeyValue("event.id",event.id()).addKeyValue("aggregate.id",event.aggregateId())
      .addKeyValue("event.type",event.eventType()).log("Domain event delivered");
 }
}
