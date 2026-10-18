package com.agentpocketmoney.agentwallet.model.wallet;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="wallet_policy_rules")
public class WalletPolicyRule {
 @Id private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="wallet_id",nullable=false,updatable=false) private Wallet wallet;
 @Enumerated(EnumType.STRING) @Column(name="rule_type",nullable=false,length=32) private PolicyRuleType ruleType;
 @Column(name="rule_key",length=64) private String ruleKey;
 @Column(name="amount_limit_minor_units") private Long amountLimitMinorUnits;
 @Column(name="velocity_count") private Integer velocityCount;
 @Column(name="window_seconds") private Long windowSeconds;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected WalletPolicyRule(){}
 public WalletPolicyRule(UUID id,Wallet wallet,PolicyRuleType type,String key,Long amount,Integer count,Long seconds,Instant now){
  this.id=id;this.wallet=wallet;this.ruleType=type;this.ruleKey=key;this.amountLimitMinorUnits=amount;
  this.velocityCount=count;this.windowSeconds=seconds;this.createdAt=now;
 }
 public UUID getId(){return id;} public Wallet getWallet(){return wallet;} public PolicyRuleType getRuleType(){return ruleType;}
 public String getRuleKey(){return ruleKey;} public Long getAmountLimitMinorUnits(){return amountLimitMinorUnits;}
 public Integer getVelocityCount(){return velocityCount;} public Long getWindowSeconds(){return windowSeconds;}
}
