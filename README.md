# Agent Wallet

Sandbox backend project for learning safe agent spending and payment systems. Phases 1–3 provide an append-only double-entry ledger, ledger-backed wallet funding, and authorization holds with available balance tracking.

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

## Run the API smoke suite in Insomnia

Import [`api-tests/insomnia-agent-wallet-phase-2.json`](api-tests/insomnia-agent-wallet-phase-2.json) into Insomnia, open the imported collection, choose **Run Collection**, keep all requests selected in order, and click **Run**. The suite creates a wallet, funds it, verifies ledger and available balances, creates and releases a hold, checks insufficient available balance and top-up idempotency, and confirms invalid requests return safe errors. The collection saves generated wallet and idempotency values between requests.

The included environment targets `http://localhost:8080` and funds the wallet with 65000 minor units (₹650.00). Change `base_url` if the app uses a different port. Each run creates new sandbox data in PostgreSQL; no real money is moved. See [`api-tests/README.md`](api-tests/README.md) for setup details.

## Logging and exception handling

- SLF4J/Logback logs are bridged into OpenTelemetry and exported through OTLP. Local log records are also written to the console with trace and span IDs when available.
- Application logs use `INFO` for meaningful business events, while dependency logs are limited to `WARN` and above. Avoid per-line/per-request success logs.
- Use structured key/value fields for identifiers and amounts. Never log credentials, tokens, card data, OTPs, or raw request payloads.
- Throw a typed application/domain exception for expected failures. Map it to a safe HTTP `ProblemDetail` at the API boundary; do not catch and suppress exceptions or log the same failure at multiple layers.
- Unexpected request failures are logged once by the global exception handler and returned as a generic 500 response without exception details.
