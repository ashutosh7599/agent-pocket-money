package com.agentpocketmoney.agentwallet.repository;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.agentpocketmoney.agentwallet.model.ledger.JournalLine;

public interface JournalLineRepository extends JpaRepository<JournalLine, UUID> {
    @Query(value = "SELECT COALESCE(SUM(CASE WHEN side = 'DEBIT' THEN amount_minor_units "
            + "ELSE -amount_minor_units END), 0) FROM journal_lines WHERE account_id = :accountId",
            nativeQuery = true)
    BigDecimal signedBalance(@Param("accountId") UUID accountId);
}
