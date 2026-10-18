# Agent Wallet

Sandbox backend project for learning safe agent spending and payment systems. Phases 1–8 provide an append-only double-entry ledger, wallet funding, authorization holds, payments, spending policies, a deterministic mock payment flow, and an outbox-backed webhook boundary.

## Requirements

- Java 21
- Maven 3.8+
- Docker Compose

## Run

Start PostgreSQL and the OpenTelemetry Collector using Compose, then run the application. Flyway creates the ledger schema on startup. The collector accepts OTLP over HTTP on port 4318 and prints a compact receipt for exported logs.

```bash
docker compose up -d postgres otel-collector
mvn spring-boot:run
```

The default local database settings match `docker-compose.yml`. Override them with `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` as needed.
Set `OTEL_EXPORTER_OTLP_ENDPOINT` to point at a different collector. Logs and traces are exported over OTLP HTTP; metrics are disabled for now.

## Package layout

Application code lives under `com.agentpocketmoney.agentwallet` and is grouped by responsibility:

- `controller` handles HTTP endpoints.
- `dto` contains request and response types.
- `exception` contains typed application errors and the API exception handler.
- `model.ledger` and `model.wallet` contain persistence/domain models.
- `repository` contains Spring Data persistence interfaces.
- `service` contains business operations.
- `config` contains application configuration and startup hooks.

Run the ledger unit tests with:

```bash
mvn test
```

## Phase 1 ledger core

- Ledger accounts record account type and currency.
- Journal entries carry a business transaction reference.
- Journal lines store positive integer minor-unit amounts and debit or credit side.
- Posting validates balanced totals, at least two lines, one currency, and existing accounts before persistence.
- Account balances are calculated from journal lines using each account type's natural balance side.
- PostgreSQL rejects updates or deletes to journal entries and lines. Corrections use reversal postings.
- PostgreSQL also checks, at commit, that every journal entry has at least two lines and balanced totals.

The ledger core is exposed through the application service and the wallet routes below.

## Phase 2 wallet API

- `POST /api/v1/wallets` creates an active INR wallet.
- `GET /api/v1/wallets/{walletId}` returns ledger balance, active held amount, and available balance.
- `POST /api/v1/wallets/{walletId}/top-ups` funds the wallet from the simulated settlement account. Send an `Idempotency-Key` header and a JSON body such as `{"amountMinorUnits":65000}`.
- `GET /api/v1/wallets/{walletId}/transactions?page=0&size=20` returns ledger-backed wallet history; page size is limited to 100.

Top-up retries with the same wallet, key, and amount return the original result. Reusing a key with a different amount returns `409 Conflict`. Top-ups are recorded as debit-to-settlement and credit-to-wallet journal lines in the same database transaction. Wallet balance is not cached.

## Phase 3 authorization holds

GET /api/v1/wallets/{walletId}/holds?status=ACTIVE&page=0&size=20 lists active, unexpired holds by default. Set status to RELEASED or EXPIRED to inspect history, then use a listed hold ID in the release request.

- `POST /api/v1/wallets/{walletId}/holds` reserves available funds. Send `{"amountMinorUnits":20000,"expiresAt":"<future ISO-8601 timestamp>"}`. The response is `201 Created` with an `ACTIVE` hold.
- `POST /api/v1/wallets/{walletId}/holds/{holdId}/release` releases an active hold. Releasing an already released or expired hold is safe and returns its current state.
- `GET /api/v1/wallets/{walletId}` keeps `balanceMinorUnits` as the ledger balance and includes `heldBalanceMinorUnits` and `availableBalanceMinorUnits`. Only active, unexpired holds count against available funds.
- A scheduled worker expires due holds every 30 seconds by default. Wallet reads and hold operations also expire due holds for that wallet. Configure `wallet.holds.expiry-poll-interval-ms` to change the poll interval.

## Phase 4 payment authorization

- POST /api/v1/wallets/{walletId}/payments creates a payment in CREATED state. Send an Idempotency-Key header and merchant, amount, category, and purpose. Matching retries return the original payment; a changed request with the same key returns HTTP 409.
- POST /api/v1/wallets/{walletId}/payments/{paymentId}/authorize authorizes the payment and reserves funds with an expiring hold. Repeated authorization requests return the same payment state without creating another hold. Insufficient available balance declines the payment.
- GET /api/v1/wallets/{walletId}/payments/{paymentId} returns the wallet-scoped payment state.

## Phase 6 policy engine

- PUT /api/v1/wallets/{walletId}/policy replaces the wallet's allowed merchant IDs, per-transaction/daily/monthly limits, category budgets, velocity window, and manual approval threshold.
- Authorization is deny-by-default: merchants must appear in the allowlist. Policy checks return reason codes for merchant, transaction, daily/monthly, category, or velocity declines. Transactions at or above the approval threshold become PENDING_APPROVAL and reserve funds.
- Every evaluation is persisted with its result and reason. Configure the policy before authorizing payments.

## Phase 5 capture, void, and refund

- POST /api/v1/wallets/{walletId}/payments/{paymentId}/capture consumes an active authorization hold and posts a balanced debit to the wallet liability and credit to a merchant payable account. Retry with the same Idempotency-Key to receive the existing captured payment.
- POST /api/v1/wallets/{walletId}/payments/{paymentId}/void releases an active hold without changing ledger balance. Retry with the same Idempotency-Key safely returns the voided payment.
- POST /api/v1/wallets/{walletId}/payments/{paymentId}/refunds accepts a positive amountMinorUnits value and an Idempotency-Key. Partial refunds are supported; the total cannot exceed the captured amount. Each refund posts balanced merchant-payable debit and wallet-liability credit lines.

## Phase 7 mock payment ecosystem

Payment creation accepts an optional gatewayScenario: APPROVE (default), DECLINE, TIMEOUT, or OTP_REQUIRED. The deterministic in-process gateway routes approved/declined/OTP requests through the mock card network and issuer, and returns gateway payment ID, network transaction ID, and issuer response code. A timeout stops before network routing. No real card data or external payment provider is used. OTP_REQUIRED leaves the payment pending with funds held for the approval flow.

## Phase 8 events and webhooks

- Payment and wallet top-up changes are written to `outbox_events` in the same transaction as the business update. A scheduled worker dispatches committed events and retries failures with backoff, moving events to `DEAD_LETTER` after five failed attempts.
- `POST /api/v1/webhooks/mock-gateway` accepts a raw JSON body and an `X-Webhook-Signature` header formatted as `sha256=<hex HMAC-SHA256>`. Set `WEBHOOK_SIGNING_SECRET` before starting the app; requests without a configured secret or with an invalid signature are rejected. A repeated valid event ID returns `DUPLICATE` without processing it again.
- The receiver stores accepted signed webhook payloads for audit and deduplication. It does not call an external gateway or require real payment credentials.

For local signature generation, set the same secret in your shell and use Python's `hmac` module to sign the exact request body bytes. Whitespace changes alter the signature.

## Phase 9 reconciliation

- `POST /api/v1/reconciliation/batches` reconciles a JSON array of settlement rows (`paymentId`, `amountMinorUnits`, `status`) against captured/refunded payments and persists the batch and any breaks.
- `POST /api/v1/reconciliation/mock-batches?scenario=MATCH` generates a matching report. Use `MISSING`, `AMOUNT_MISMATCH`, `STATUS_MISMATCH`, or `UNKNOWN_PAYMENT` to exercise break detection.
- `GET /api/v1/reconciliation/batches/{batchId}` reads a persisted batch; `GET /api/v1/reconciliation/batches/{batchId}/breaks` returns its stored breaks.

## Phase 10 security and audit

All `/api/v1/**` endpoints require an API key in the `X-API-Key` header. Configure keys only in the runtime environment using `WALLET_API_KEYS` with comma-separated `key:ROLE[:wallet-uuid]` entries. `ADMIN` keys can administer wallets, funding, policies, audit queries, and reconciliation. `AGENT` keys must include one wallet UUID and can operate only on that wallet's payment and hold endpoints. `WEBHOOK` keys are limited to the mock gateway webhook receiver. Keep these values in a local secret manager or untracked environment file; never commit them.

Successful wallet, hold, payment, policy, and reconciliation operations write append-only audit rows with the actor key fingerprint, action, entity ID, trace ID, and timestamp. Admins can inspect them at `GET /api/v1/admin/audit-events?page=0&size=20`.

## Phase 11 agent payment interface

- An admin creates a one-time spend mandate under a wallet with purpose, merchant, category, currency, maximum amount, and expiry.
- An agent requests payment using `POST /api/v1/wallets/{walletId}/mandates/{mandateId}/payments` and an `Idempotency-Key`. The API enforces the mandate, creates and authorizes the payment, and returns the resulting payment state. A repeated request with the same key returns the same payment; a different request cannot reuse the consumed mandate.
- Payments awaiting manual/OTP approval can be approved or rejected by an admin using the wallet payment `/approve` or `/reject` routes with an `Idempotency-Key`. Rejection releases the authorization hold.

## Run the API smoke suite in Insomnia

Import [`api-tests/insomnia-agent-wallet-phase-2.json`](api-tests/insomnia-agent-wallet-phase-2.json) into Insomnia, open the imported collection, choose **Run Collection**, keep all requests selected in order, and click **Run**. The suite creates a wallet, funds it, verifies ledger and available balances, creates, lists, and releases a hold, checks policy setup, payment authorization, capture, partial/full refund, gateway outcomes, and idempotency, insufficient available balance, and invalid requests. The collection saves generated wallet and idempotency values between requests.

The included environment targets `http://localhost:8080`, includes a blank `api_key` variable, and funds the wallet with 65000 minor units (₹650.00). Set `api_key` to an admin key for the full suite and change `base_url` if the app uses a different port. Each run creates new sandbox data in PostgreSQL; no real money is moved. See [`api-tests/README.md`](api-tests/README.md) for setup details.

## Logging and exception handling

- SLF4J/Logback logs are bridged into OpenTelemetry and exported through OTLP. Local log records are also written to the console with trace and span IDs when available.
- Application logs use `INFO` for meaningful business events, while dependency logs are limited to `WARN` and above. Avoid per-line/per-request success logs.
- Use structured key/value fields for identifiers and amounts. Never log credentials, tokens, card data, OTPs, or raw request payloads.
- Throw a typed application/domain exception for expected failures. Map it to a safe HTTP `ProblemDetail` at the API boundary; do not catch and suppress exceptions or log the same failure at multiple layers.
- Unexpected request failures are logged once by the global exception handler and returned as a generic 500 response without exception details.
