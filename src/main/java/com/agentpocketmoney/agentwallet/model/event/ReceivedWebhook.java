package com.agentpocketmoney.agentwallet.model.event;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
@Entity @Table(name="received_webhooks")
public class ReceivedWebhook {
 @Id private UUID id;
 @Column(name="event_id",nullable=false,unique=true) private UUID eventId;
 @Column(name="event_type",nullable=false,length=96) private String eventType;
 @Column(name="signature_valid",nullable=false) private boolean signatureValid;
 @Column(nullable=false,columnDefinition="TEXT") private String payload;
 @Column(nullable=false) private boolean processed;
 @Column(name="received_at",nullable=false,updatable=false) private Instant receivedAt;
 @Column(name="processed_at") private Instant processedAt;
 protected ReceivedWebhook(){}
 public ReceivedWebhook(UUID eventId,String type,boolean valid,String payload,Instant now){
  this.id=UUID.randomUUID();this.eventId=eventId;this.eventType=type;this.signatureValid=valid;
  this.payload=payload;this.receivedAt=now.truncatedTo(ChronoUnit.MICROS);this.processed=valid;
  this.processedAt=valid?this.receivedAt:null;
 }
 public UUID getId(){return id;} public UUID getEventId(){return eventId;} public String getEventType(){return eventType;}
 public boolean isSignatureValid(){return signatureValid;} public String getPayload(){return payload;}
 public boolean isProcessed(){return processed;} public Instant getReceivedAt(){return receivedAt;} public Instant getProcessedAt(){return processedAt;}
}
