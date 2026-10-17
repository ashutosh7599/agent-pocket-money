package com.agentpocketmoney.agentwallet.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.service.LedgerService;
import com.agentpocketmoney.agentwallet.service.WalletService;
import com.agentpocketmoney.agentwallet.repository.LedgerAccountRepository;

@Component
public class SandboxSettlementAccountInitializer implements ApplicationRunner {
    private final LedgerAccountRepository accounts;
    private final LedgerService ledger;

    public SandboxSettlementAccountInitializer(LedgerAccountRepository accounts, LedgerService ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accounts.findByCode(WalletService.SANDBOX_SETTLEMENT_ACCOUNT_CODE).isEmpty()) {
            ledger.createAccount(WalletService.SANDBOX_SETTLEMENT_ACCOUNT_CODE,
                    "Sandbox settlement asset", AccountType.ASSET, WalletService.DEFAULT_CURRENCY);
        }
    }
}
