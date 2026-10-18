# Phase 12 safety test report

Run date: 2026-10-18

## Automated coverage

`mvn test` covers payment state transitions, policy decisions, mock gateway outcomes, webhook signature/replay handling, reconciliation break persistence, mandate limits and retries, and API-key scope enforcement. The PostgreSQL integration test uses Testcontainers and covers concurrent double-spend prevention, duplicate top-up/payment idempotency, concurrent authorization of one payment, signed webhook replay, and persisted reconciliation breaks.

In this WSL environment Maven's Testcontainers Docker discovery returned an invalid Docker `/info` response, so the three PostgreSQL integration cases were skipped by `@Testcontainers(disabledWithoutDocker=true)`. The container-backed cases will run automatically where Testcontainers can reach Docker.

## Live PostgreSQL stress run

The same safety boundaries were exercised through the running API against the Compose PostgreSQL database:

| Scenario | Requests | Result |
| --- | ---: | --- |
| Wallet funded with 500 minor units; authorize 10-unit payments concurrently | 100 | 50 authorized, 50 declined; ledger balance 500, held 500, available 0 |
| Authorize one 50-unit payment concurrently | 12 | All 12 returned `AUTHORIZED`; exactly 50 held, available remained 450 |
| Concurrent top-up retries with one idempotency key | 12 | 1 top-up ID returned; wallet credited once with 500 |
| Concurrent payment creation retries with one idempotency key | 12 | 1 payment ID returned |

No request produced a negative available balance or duplicate credit/payment record.
