package com.agentpocketmoney.agentwallet.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.agentpocketmoney.agentwallet.model.wallet.WalletTransactionRecord;

public interface WalletTransactionRepository extends JpaRepository<com.agentpocketmoney.agentwallet.model.ledger.JournalLine, UUID> {
    @Query("select new com.agentpocketmoney.agentwallet.model.wallet.WalletTransactionRecord(entry.id, "
            + "entry.transactionReference, entry.description, line.side, line.amountMinorUnits, "
            + "line.currency, entry.postedAt) "
            + "from JournalLine line join line.journalEntry entry "
            + "where line.account.id = :accountId order by entry.postedAt desc, entry.id desc")
    Page<WalletTransactionRecord> findWalletTransactions(@Param("accountId") UUID accountId, Pageable pageable);
}
