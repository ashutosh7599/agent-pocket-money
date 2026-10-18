package com.agentpocketmoney.agentwallet.repository;

import com.agentpocketmoney.agentwallet.model.agent.SpendMandate;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpendMandateRepository extends JpaRepository<SpendMandate,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from SpendMandate m where m.wallet.id=:walletId and m.id=:mandateId")
    Optional<SpendMandate> findForUpdate(@Param("walletId") UUID walletId,@Param("mandateId") UUID mandateId);
}
