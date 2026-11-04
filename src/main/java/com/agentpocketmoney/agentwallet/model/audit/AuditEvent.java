package com.agentpocketmoney.agentwallet.model.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name="audit_events")
public class AuditEvent {
    @Id private UUID id;
    @Column(nullable=false,length=128) private String actor;
    @Column(nullable=false,length=48) private String action;
    @Column(name="entity_type",nullable=false,length=48) private String entityType;
    @Column(name="entity_id") private UUID entityId;
    @Column(name="request_id",length=64) private String requestId;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected AuditEvent(){}
    public AuditEvent(UUID id,String actor,String action,String entityType,UUID entityId,String requestId,Instant createdAt){
        this.id=id;this.actor=actor;this.action=action;this.entityType=entityType;this.entityId=entityId;this.requestId=requestId;
        this.createdAt=createdAt.truncatedTo(ChronoUnit.MICROS);
    }
    public UUID getId(){return id;} public String getActor(){return actor;} public String getAction(){return action;}
    public String getEntityType(){return entityType;} public UUID getEntityId(){return entityId;}
    public String getRequestId(){return requestId;} public Instant getCreatedAt(){return createdAt;}
}
