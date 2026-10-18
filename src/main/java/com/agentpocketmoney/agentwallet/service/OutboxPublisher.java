package com.agentpocketmoney.agentwallet.service;
import com.agentpocketmoney.agentwallet.model.event.*;
import com.agentpocketmoney.agentwallet.repository.OutboxEventRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class OutboxPublisher {
 private static final Logger logger=LoggerFactory.getLogger(OutboxPublisher.class);
 private static final int MAX_ATTEMPTS=5;
 private final OutboxEventRepository events;private final ApplicationEventPublisher publisher;private final Clock clock;
 public OutboxPublisher(OutboxEventRepository events,ApplicationEventPublisher publisher,Clock clock){this.events=events;this.publisher=publisher;this.clock=clock;}
 @Scheduled(fixedDelayString="${outbox.poll-interval-ms:1000}")
 @Transactional
 public void publishDue(){
  var due=events.findDueForUpdate(OutboxStatus.PENDING,clock.instant(),PageRequest.of(0,100));
  for(OutboxEvent event:due){
   try{
    publisher.publishEvent(new OutboxDomainEvent(event.getId(),event.getAggregateType(),event.getAggregateId(),
        event.getEventType(),event.getPayload(),event.getCreatedAt()));
    event.published(clock.instant());
    logger.atInfo().addKeyValue("outbox.event.id",event.getId()).addKeyValue("event.type",event.getEventType()).log("Outbox event published");
   }catch(RuntimeException failure){
    event.failed(failure.getClass().getSimpleName(),clock.instant(),MAX_ATTEMPTS);
    logger.atWarn().addKeyValue("outbox.event.id",event.getId()).addKeyValue("attempt",event.getAttemptCount())
        .addKeyValue("outbox.status",event.getStatus()).log("Outbox event dispatch failed");
   }
  }
 }
}
