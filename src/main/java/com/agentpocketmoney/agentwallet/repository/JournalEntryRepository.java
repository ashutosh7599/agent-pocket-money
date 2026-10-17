package com.agentpocketmoney.agentwallet.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import com.agentpocketmoney.agentwallet.model.ledger.JournalEntry;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {}
