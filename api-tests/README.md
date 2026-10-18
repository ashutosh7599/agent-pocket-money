# Agent Wallet API smoke suite (Insomnia)

## Run it

1. Start PostgreSQL and the app with `docker compose up -d postgres otel-collector` and `mvn spring-boot:run`.
2. In Insomnia, choose **Import** and select `insomnia-agent-wallet-phase-2.json`.
3. Open **Agent Wallet - Wallet and Holds API Smoke Suite** and choose **Run Collection**.
4. Keep all requests selected in their listed order, select **Base Environment**, and click **Run**.

The environment defaults to `http://localhost:8080`. Change `base_url` if the app uses another host or port. The top-up amount defaults to `65000` minor units (₹650.00); the conflicting amount defaults to 65001 so the idempotency check returns a conflict.

The create-wallet response saves its ID and a wallet-specific idempotency key automatically. Later requests reuse those values. Response scripts verify ledger/available balances, hold creation/listing/release, payment creation/idempotency/authorization/capture/refunds, mock gateway outcomes, reconciliation batch creation and break queries, insufficient available balance, ledger-backed transaction history, and safe validation errors.

Each run creates a new wallet, one sandbox top-up, and an authorization hold that is released before the suite finishes in the configured database. The top-up is simulated and does not move real money; the data is persisted so the run can be inspected afterward.
