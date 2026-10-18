package com.agentpocketmoney.agentwallet.repository;
import com.agentpocketmoney.agentwallet.model.wallet.PolicyEvaluation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PolicyEvaluationRepository extends JpaRepository<PolicyEvaluation,UUID> {}
