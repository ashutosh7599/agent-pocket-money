package com.agentpocketmoney.agentwallet.model.event;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
@Entity @Table(name="outbox_events")
public class OutboxEvent {
 @Id private UUID id;
 @Column(name="aggregate_type",nullable=false,length=64) private String aggregateType;
 @Column(name="aggregate_id",nullable=false) private UUID aggregateId;
 @Column(name="event_type",nullable=false,length=96) private String eventType;
 @Column(nullable=false,columnDefinition="TEXT") private String payload;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private OutboxStatus status;
 @Column(name="attempt_count",nullable=false) private int attemptCount;
 @Column(name="next_attempt_at",nullable=false) private Instant nextAttemptAt;
 @Column(name="last_error",length=256) private String lastError;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="published_at") private Instant publishedAt;
 protected OutboxEvent(){}
 public OutboxEvent(UUID id,String aggregateType,UUID aggregateId,String eventType,String payload,Instant now){
  this.id=id;this.aggregateType=aggregateType;this.aggregateId=aggregateId;this.eventType=eventType;this.payload=payload;
  this.status=OutboxStatus.PENDING;this.attemptCount=0;this.createdAt=now.truncatedTo(ChronoUnit.MICROS);
  this.nextAttemptAt=this.createdAt;
 }
 public void published(Instant now){status=OutboxStatus.PUBLISHED;publishedAt=now.truncatedTo(ChronoUnit.MICROS);lastError=null;}
 public void failed(String message,Instant now,int maxAttempts){
  attemptCount++;lastError=message==null?"Dispatch failed":message.substring(0,Math.min(256,message.length()));
  if(attemptCount>=maxAttempts) status=OutboxStatus.DEAD_LETTER;
  else nextAttemptAt=now.plusSeconds(Math.min(300L,1L<<Math.min(attemptCount,8)));
 }
 public UUID getId(){return id;} public String getAggregateType(){return aggregateType;} public UUID getAggregateId(){return aggregateId;}
 public String getEventType(){return eventType;} public String getPayload(){return payload;} public OutboxStatus getStatus(){return status;}
 public int getAttemptCount(){return attemptCount;} public Instant getNextAttemptAt(){return nextAttemptAt;}
 public String getLastError(){return lastError;} public Instant getCreatedAt(){return createdAt;} public Instant getPublishedAt(){return publishedAt;}
}
