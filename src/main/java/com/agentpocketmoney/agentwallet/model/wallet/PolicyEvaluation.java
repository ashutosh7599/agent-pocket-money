package com.agentpocketmoney.agentwallet.model.wallet;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="policy_evaluations")
public class PolicyEvaluation {
 @Id private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="wallet_id",nullable=false,updatable=false) private Wallet wallet;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_id",nullable=false,updatable=false) private Payment payment;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private PolicyDecision decision;
 @Column(name="reason_code",length=64) private String reasonCode;
 @Column(name="evaluated_at",nullable=false,updatable=false) private Instant evaluatedAt;
 protected PolicyEvaluation(){}
 public PolicyEvaluation(UUID id,Wallet wallet,Payment payment,PolicyDecision decision,String reason,Instant at){
  this.id=id;this.wallet=wallet;this.payment=payment;this.decision=decision;this.reasonCode=reason;this.evaluatedAt=at;
 }
 public UUID getId(){return id;} public PolicyDecision getDecision(){return decision;} public String getReasonCode(){return reasonCode;}
}
