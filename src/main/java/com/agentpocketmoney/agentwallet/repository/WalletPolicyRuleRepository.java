package com.agentpocketmoney.agentwallet.repository;
import com.agentpocketmoney.agentwallet.model.wallet.WalletPolicyRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WalletPolicyRuleRepository extends JpaRepository<WalletPolicyRule,UUID> {
 List<WalletPolicyRule> findByWalletId(UUID walletId);
 void deleteByWalletId(UUID walletId);
}
