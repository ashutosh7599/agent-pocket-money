package com.agentpocketmoney.agentwallet.service;

import com.agentpocketmoney.agentwallet.config.AfterCommitAction;
import com.agentpocketmoney.agentwallet.dto.CategoryBudgetRequest;
import com.agentpocketmoney.agentwallet.dto.WalletPolicyRequest;
import com.agentpocketmoney.agentwallet.dto.WalletPolicyResponse;
import com.agentpocketmoney.agentwallet.model.wallet.*;
import com.agentpocketmoney.agentwallet.repository.*;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.agentpocketmoney.agentwallet.audit.Audited;

@Service
public class WalletPolicyService {
 private static final List<PaymentStatus> SPEND_STATUSES=List.of(PaymentStatus.AUTHORIZED,PaymentStatus.PENDING_APPROVAL,
     PaymentStatus.CAPTURED,PaymentStatus.PARTIALLY_REFUNDED,PaymentStatus.REFUNDED);
 private static final Logger logger=LoggerFactory.getLogger(WalletPolicyService.class);
 private final WalletRepository wallets; private final WalletPolicyRuleRepository rules;
 private final PolicyEvaluationRepository evaluations; private final PaymentRepository payments; private final Clock clock;
 public WalletPolicyService(WalletRepository wallets,WalletPolicyRuleRepository rules,PolicyEvaluationRepository evaluations,
     PaymentRepository payments,Clock clock){this.wallets=wallets;this.rules=rules;this.evaluations=evaluations;this.payments=payments;this.clock=clock;}

 @Transactional
 @Audited(action="WALLET_POLICY_REPLACED",entityType="wallet")
 public WalletPolicyResponse replace(UUID walletId,WalletPolicyRequest request){
  Wallet wallet=wallets.findByIdForUpdate(walletId).orElseThrow(()->new WalletException(
      WalletErrorCode.WALLET_NOT_FOUND,"Wallet not found."));
  if(request.velocityMaxTransactions()!=null != (request.velocityWindowSeconds()!=null))
   throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST,
       "Velocity count and window must be configured together.");
  if(request.allowedMerchantIds().stream().anyMatch(x->x==null||x.isBlank())||
      request.allowedMerchantIds().stream().distinct().count()!=request.allowedMerchantIds().size())
   throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST,
       "Allowed merchant IDs must be non-empty and unique.");
  Instant now=clock.instant(); rules.deleteByWalletId(walletId);
  List<WalletPolicyRule> next=new ArrayList<>();
  for(String merchant:request.allowedMerchantIds()) next.add(rule(wallet,PolicyRuleType.MERCHANT_ALLOWLIST,merchant,1L,null,null,now));
  add(next,wallet,PolicyRuleType.MAX_TRANSACTION_AMOUNT,null,request.maxTransactionAmountMinorUnits(),now);
  add(next,wallet,PolicyRuleType.DAILY_SPEND_LIMIT,null,request.dailyLimitMinorUnits(),now);
  add(next,wallet,PolicyRuleType.MONTHLY_SPEND_LIMIT,null,request.monthlyLimitMinorUnits(),now);
  add(next,wallet,PolicyRuleType.MANUAL_APPROVAL_THRESHOLD,null,request.manualApprovalThresholdMinorUnits(),now);
  if(request.velocityMaxTransactions()!=null) next.add(rule(wallet,PolicyRuleType.VELOCITY_LIMIT,null,null,
      request.velocityMaxTransactions(),request.velocityWindowSeconds(),now));
  if(request.categoryBudgets()!=null){
   if(request.categoryBudgets().stream().map(CategoryBudgetRequest::category).distinct().count()!=request.categoryBudgets().size())
    throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST,"Category budget categories must be unique.");
   for(CategoryBudgetRequest b:request.categoryBudgets()) next.add(rule(wallet,PolicyRuleType.CATEGORY_BUDGET,b.category(),b.limitMinorUnits(),null,null,now));
  }
  rules.saveAll(next);
  AfterCommitAction.run(()->logger.atInfo().addKeyValue("wallet.id",walletId).addKeyValue("policy.rule_count",next.size()).log("Wallet spending policy updated"));
  return response(walletId,next);
 }

 @Transactional
 public Decision evaluate(Wallet wallet,Payment payment,Instant now){
  List<WalletPolicyRule> configured=rules.findByWalletId(wallet.getId());
  Decision decision=evaluateRules(configured,payment,now);
  evaluations.save(new PolicyEvaluation(UUID.randomUUID(),wallet,payment,decision.result(),decision.reasonCode(),now));
  return decision;
 }

 private Decision evaluateRules(List<WalletPolicyRule> configured,Payment payment,Instant now){
  if(configured.stream().noneMatch(r->r.getRuleType()==PolicyRuleType.MERCHANT_ALLOWLIST&&payment.getMerchantId().equals(r.getRuleKey())))
   return new Decision(PolicyDecision.DECLINED,"MERCHANT_NOT_ALLOWED");
  for(WalletPolicyRule rule:configured){
   PolicyRuleType t=rule.getRuleType(); long amount=payment.getAmountMinorUnits();
   if(t==PolicyRuleType.MAX_TRANSACTION_AMOUNT&&amount>rule.getAmountLimitMinorUnits()) return declined("TRANSACTION_LIMIT_EXCEEDED");
   if(t==PolicyRuleType.DAILY_SPEND_LIMIT&&amount+payments.sumSpendSince(payment.getWallet().getId(),now.truncatedTo(ChronoUnit.DAYS),SPEND_STATUSES)>rule.getAmountLimitMinorUnits()) return declined("DAILY_LIMIT_EXCEEDED");
   if(t==PolicyRuleType.MONTHLY_SPEND_LIMIT){
    Instant start=now.atZone(java.time.ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
    if(amount+payments.sumSpendSince(payment.getWallet().getId(),start,SPEND_STATUSES)>rule.getAmountLimitMinorUnits()) return declined("MONTHLY_LIMIT_EXCEEDED");
   }
   if(t==PolicyRuleType.CATEGORY_BUDGET&&payment.getCategory().equals(rule.getRuleKey())
       &&amount+payments.sumCategorySpendSince(payment.getWallet().getId(),payment.getCategory(),now.truncatedTo(ChronoUnit.DAYS),SPEND_STATUSES)>rule.getAmountLimitMinorUnits())
    return declined("CATEGORY_BUDGET_EXCEEDED");
   if(t==PolicyRuleType.VELOCITY_LIMIT&&payments.countSpendSince(payment.getWallet().getId(),now.minusSeconds(rule.getWindowSeconds()),SPEND_STATUSES)>=rule.getVelocityCount())
    return declined("VELOCITY_LIMIT_EXCEEDED");
  }
  for(WalletPolicyRule rule:configured)
   if(rule.getRuleType()==PolicyRuleType.MANUAL_APPROVAL_THRESHOLD&&payment.getAmountMinorUnits()>=rule.getAmountLimitMinorUnits())
    return new Decision(PolicyDecision.PENDING_APPROVAL,"MANUAL_APPROVAL_REQUIRED");
  return new Decision(PolicyDecision.APPROVED,null);
 }
 private static Decision declined(String reason){return new Decision(PolicyDecision.DECLINED,reason);}
 private static WalletPolicyRule rule(Wallet w,PolicyRuleType t,String key,Long amount,Integer count,Long seconds,Instant now){return new WalletPolicyRule(UUID.randomUUID(),w,t,key,amount,count,seconds,now);}
 private static void add(List<WalletPolicyRule> out,Wallet w,PolicyRuleType t,String key,Long amount,Instant now){if(amount!=null)out.add(rule(w,t,key,amount,null,null,now));}
 private static WalletPolicyResponse response(UUID id,List<WalletPolicyRule> rs){
  List<String> merchants=rs.stream().filter(r->r.getRuleType()==PolicyRuleType.MERCHANT_ALLOWLIST).map(WalletPolicyRule::getRuleKey).toList();
  Long max=find(rs,PolicyRuleType.MAX_TRANSACTION_AMOUNT),daily=find(rs,PolicyRuleType.DAILY_SPEND_LIMIT),monthly=find(rs,PolicyRuleType.MONTHLY_SPEND_LIMIT),approval=find(rs,PolicyRuleType.MANUAL_APPROVAL_THRESHOLD);
  WalletPolicyRule velocity=rs.stream().filter(r->r.getRuleType()==PolicyRuleType.VELOCITY_LIMIT).findFirst().orElse(null);
  List<CategoryBudgetRequest> budgets=rs.stream().filter(r->r.getRuleType()==PolicyRuleType.CATEGORY_BUDGET).map(r->new CategoryBudgetRequest(r.getRuleKey(),r.getAmountLimitMinorUnits())).toList();
  return new WalletPolicyResponse(id,merchants,max,daily,monthly,approval,velocity==null?null:velocity.getVelocityCount(),velocity==null?null:velocity.getWindowSeconds(),budgets);
 }
 private static Long find(List<WalletPolicyRule> rs,PolicyRuleType t){return rs.stream().filter(r->r.getRuleType()==t).map(WalletPolicyRule::getAmountLimitMinorUnits).findFirst().orElse(null);}
 public record Decision(PolicyDecision result,String reasonCode){}
}
