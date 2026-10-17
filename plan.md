# Agent Wallet – High-Level Design and Implementation Plan

## 1. Project Objective

Build a backend learning project called **Agent Wallet** that allows an AI agent to spend money only within safe, user-defined limits.

The system should simulate how a real payment system works:

- Wallet balance management
- Double-entry ledger
- Payment authorization
- Capture, void, refund
- Merchant allowlist
- Spending limits
- OTP-like approval simulation
- Webhooks
- Reconciliation
- Audit logging
- Concurrency safety
- Idempotency

The project should be implemented primarily using:

- Java 21
- Spring Boot 3
- PostgreSQL
- Flyway
- Spring Data JPA
- Spring Security
- Docker Compose
- Testcontainers
- OpenAPI documentation

The system should **not** initially connect to real banks, real credit cards, real Swiggy/Amazon, or real money movement systems.

The first version should be a **simulated payment ecosystem** that teaches backend payment concepts deeply.

---

# 2. Core Problem Statement

A user wants to give an AI agent limited spending power.

Example task:

> Order food from a allowed merchant, choose the cheapest eligible option, and pay automatically without asking for OTP every time.

The user does not want to give the agent direct access to a real credit card or bank account.

Instead:

- The agent gets a wallet.
- The wallet is preloaded with limited funds.
- The agent can spend only on allowed merchants.
- The agent has transaction limits, daily limits, and budget limits.
- The agent cannot overspend.
- The system records every transaction.
- High-risk payments require human approval.

---

# 3. Project Scope

## In Scope

Build a backend system that supports:

1. **Agent wallet**
   - Create wallet
   - Load wallet balance
   - Check balance
   - Freeze/unfreeze wallet
   - Transaction history

2. **Double-entry ledger**
   - Immutable journal entries
   - Account balances derived from entries
   - No direct balance edits
   - Reversals instead of modifications

3. **Payment lifecycle**
   - Authorize
   - Capture
   - Void
   - Refund
   - Partial refund
   - Authorization expiry

4. **Policy engine**
   - Merchant allowlist
   - Per-transaction limit
   - Daily limit
   - Monthly limit
   - Category budget
   - Velocity checks
   - Human approval threshold

5. **Mock payment ecosystem**
   - Mock merchant
   - Mock payment gateway
   - Mock card network
   - Mock issuer bank
   - Simulated OTP/approval step

6. **Reliability patterns**
   - Idempotency keys
   - Outbox pattern
   - Webhook delivery with retries
   - At-least-once event processing
   - Duplicate-safe consumers

7. **Reconciliation**
   - Match internal ledger with mock gateway settlement report
   - Detect mismatches
   - Report breaks

8. **Security and audit**
   - Scoped tokens
   - Role-based access
   - Audit log
   - Signed webhooks
   - No real card data storage

9. **Agent interface**
   - REST API for agent
   - Optional MCP/tool interface later

---

## Out of Scope for Initial Version

Do not implement initially:

- Real Swiggy/Amazon automation
- Real bank account integration
- Real credit card charging
- Real SMS OTP
- Production-grade KYC
- RBI-licensed wallet compliance
- Multi-currency support
- Microservices decomposition
- Full PCI-DSS certification
- Production deployment hardening

These can be discussed as future extensions, but the first implementation should remain simulated and safe.

---

# 4. High-Level Product Behavior

A typical flow:

1. User creates an agent wallet.
2. User loads money into the wallet.
3. User configures rules:
   - Allowed merchants
   - Maximum amount per transaction
   - Daily spending cap
   - Monthly spending cap
   - Category budgets
   - Transactions above a threshold require approval
4. Agent receives a task:
   - Example: buy grocery items under ₹600 from allowed merchant.
5. Agent checks:
   - Wallet balance
   - Allowed merchants
   - Product price
   - Budget remaining
6. Agent initiates payment.
7. Wallet system checks policy.
8. If approved:
   - Amount is held.
   - Mock gateway processes payment.
   - Mock issuer approves or declines.
   - Payment is captured.
   - Ledger entries are written.
9. If payment fails:
   - Hold is released.
   - Agent receives failure reason.
10. Reconciliation job verifies internal ledger against mock gateway settlement.

---

# 5. System Architecture

Use a **modular monolith** first.

Do not start with multiple microservices. Instead, build one Spring Boot application with well-separated modules.

## High-Level Components

```text
+-------------------+
|    Agent Client   |
+-------------------+
          |
          | Scoped token / API key
          v
+--------------------------------------------------+
|                  Wallet API Layer                |
|  REST Controllers, Authentication, Validation    |
+--------------------------------------------------+
          |
          v
+-------------------+        +---------------------+
|  Policy Engine    | -----> |   Wallet Service    |
+-------------------+        +---------------------+
          |                           |
          v                           v
+-------------------+        +---------------------+
| Payment Orchestr. | -----> |   Ledger Service    |
+-------------------+        +---------------------+
          |
          v
+--------------------------------------------------+
|             Mock Payment Ecosystem               |
|  Mock Gateway, Mock Card Network, Mock Issuer    |
+--------------------------------------------------+
          |
          v
+-------------------+
| Mock Merchant App |
+-------------------+

Async Infrastructure:
- Outbox table
- Event publisher
- Webhook dispatcher
- Retry worker

Operations:
- Reconciliation job
- Audit log
- Admin/API ops
```

---

# 6. Main Modules

## 6.1 Authentication and Authorization Module

Responsible for:

- Authenticating users, agents, merchants, admins
- Issuing scoped tokens
- Enforcing permissions

Suggested implementation:

- Spring Security
- JWT or API-key based auth for initial version
- Roles:
  - `USER`
  - `AGENT`
  - `MERCHANT`
  - `ADMIN`

Agent token scopes should include:

- `wallet:read`
- `payment:create`
- `payment:read`
- Possibly `payment:request_approval`

Agent should not have:

- Wallet top-up permission unless explicitly allowed
- Admin permission
- Policy modification permission
- Access to raw secrets or bank/card data

---

## 6.2 Wallet Module

Responsible for:

- Wallet creation
- Balance retrieval
- Wallet status
- Wallet freeze/unfreeze
- Wallet funding in sandbox mode

Key concepts:

- Available balance
- Ledger balance
- Held amount
- Wallet currency
- Wallet status

Suggested wallet states:

- `ACTIVE`
- `FROZEN`
- `CLOSED`

Balance calculation:

```text
available_balance = ledger_balance - active_holds
```

Important rule:

- Do not directly update wallet balance as the primary source of truth.
- Ledger entries are the source of truth.
- Cached balance can exist for performance, but must be reconcilable with ledger.

---

## 6.3 Ledger Module

This is the most important learning component.

The ledger must be double-entry and append-only.

### Core Rules

1. Every money movement has at least two entries.
2. Total debits must equal total credits for a transaction.
3. Entries are immutable.
4. Corrections are made through reversal entries.
5. Ledger entries must be linked to a business transaction.
6. Ledger must be queryable by account, wallet, transaction, and time.

### Suggested Ledger Account Types

Examples:

- `WALLET_LIABILITY`
  - Represents money owed to the user/agent.
- `SETTLEMENT_ASSET`
  - Represents funds received during top-up simulation.
- `MERCHANT_PAYABLE`
  - Money owed to merchant after capture.
- `FEE_INCOME`
  - Platform or gateway fee income.
- `REFUND_ACCOUNT`
  - Used for refund flows.
- `HOLD_ACCOUNT`
  - Optional if holds are represented in ledger.

### Example Accounting Flows

#### Wallet Top-Up

When wallet is funded with ₹500:

```text
Debit: Settlement Asset Account
Credit: Wallet Liability Account
```

Meaning:

- The system has received value.
- The wallet owes ₹500 to the user/agent.

#### Payment Capture

When agent pays ₹300 to merchant:

```text
Debit: Wallet Liability Account
Credit: Merchant Payable Account
```

Meaning:

- Wallet balance reduces.
- Merchant is owed ₹300.

#### Refund

When ₹300 is refunded:

```text
Debit: Merchant Payable Account
Credit: Wallet Liability Account
```

Meaning:

- Merchant payable reduces.
- Wallet balance increases.

#### Fee Example

If gateway charges ₹5 fee:

```text
Debit: Fee Expense / Merchant Payable adjustment
Credit: Fee Income
```

Exact accounting can be refined later, but the system must always maintain balanced entries.

---

## 6.4 Payment Lifecycle Module

This module manages the state of a payment.

### Payment States

Suggested states:

```text
CREATED
POLICY_PENDING
POLICY_DECLINED
AUTHORIZED
AUTHORIZATION_FAILED
CAPTURED
VOIDED
REFUND_PENDING
PARTIALLY_REFUNDED
REFUNDED
FAILED
EXPIRED
```

### Basic Payment Flow

```text
CREATED
  -> POLICY_PENDING
  -> AUTHORIZED
  -> CAPTURED
```

Failure paths:

```text
POLICY_PENDING -> POLICY_DECLINED
AUTHORIZED -> AUTHORIZATION_FAILED
AUTHORIZED -> VOIDED
AUTHORIZED -> EXPIRED
CAPTURED -> REFUND_PENDING -> REFUNDED
CAPTURED -> REFUND_PENDING -> PARTIALLY_REFUNDED
```

### Authorization vs Capture

- Authorization:
  - Checks policy.
  - Holds funds.
  - Gets approval from mock issuer.
- Capture:
  - Completes payment.
  - Writes ledger entries.
  - Makes merchant payable.

### Void

Void cancels an authorized payment before capture.

Effect:

- Release hold.
- No final money movement.

### Refund

Refund happens after capture.

Support:

- Full refund
- Partial refund
- Multiple partial refunds up to captured amount

Rules:

- Refund amount cannot exceed captured amount minus already refunded amount.
- Refunds must be idempotent.
- Refunds must create reversal/accounting entries.

---

## 6.5 Policy Engine Module

The policy engine decides whether an agent is allowed to make a payment.

### Policy Checks

The policy engine should evaluate:

1. Wallet status
   - Wallet must be active.

2. Merchant allowlist
   - Merchant must be allowed.

3. Merchant category
   - Category must be allowed.

4. Transaction amount
   - Must be below per-transaction max.

5. Available balance
   - Must be sufficient.

6. Daily limit
   - Total spend today must remain under limit.

7. Monthly limit
   - Total spend this month must remain under limit.

8. Category budget
   - Example: food budget ₹2000/month.

9. Velocity rules
   - Example: max 5 transactions in 10 minutes.

10. Approval threshold
   - Example: payments above ₹1000 require human approval.

### Policy Result

Policy engine should return:

```text
APPROVED
DECLINED
PENDING_APPROVAL
```

Decline reasons should be explicit:

- `MERCHANT_NOT_ALLOWED`
- `INSUFFICIENT_BALANCE`
- `TRANSACTION_LIMIT_EXCEEDED`
- `DAILY_LIMIT_EXCEEDED`
- `MONTHLY_LIMIT_EXCEEDED`
- `CATEGORY_BUDGET_EXCEEDED`
- `VELOCITY_LIMIT_EXCEEDED`
- `WALLET_FROZEN`
- `MANUAL_APPROVAL_REQUIRED`

### Policy Configuration

Suggested entities:

- `PolicyRule`
- `MerchantAllowlist`
- `SpendingLimit`
- `CategoryBudget`
- `ApprovalRule`

Policy configuration should be stored in database, not hardcoded.

---

## 6.6 Mock Merchant Module

Create one or more mock merchants.

Examples:

- FoodApp
- GroceryApp
- ElectronicsApp

Each merchant can expose simple APIs:

- List products
- Create order
- Get order status
- Request payment
- Confirm payment

The merchant should not store card details.

It should interact with the mock payment gateway.

Example product model:

```text
Product:
- id
- name
- category
- price
- currency
- stock
```

Example order model:

```text
Order:
- id
- merchant_id
- items
- total_amount
- currency
- status
- payment_id
```

Order states:

```text
CREATED
PAYMENT_PENDING
PAID
FAILED
CANCELLED
```

---

## 6.7 Mock Payment Gateway Module

This simulates an acquirer/payment gateway.

Responsibilities:

- Receive payment request from merchant or wallet orchestrator
- Forward to mock card network
- Return authorization result
- Generate gateway payment ID
- Emit webhooks
- Generate settlement reports

Gateway should simulate:

- Success
- Decline
- Timeout
- Network failure
- Duplicate request handling
- Refund processing

Gateway should support test configuration:

```text
success_rate
latency_ms
decline_code_probability
otp_required_probability
```

---

## 6.8 Mock Card Network Module

This simulates the card network layer.

Responsibilities:

- Route authorization request to mock issuer
- Validate tokenized payment credential
- Return approval/decline
- Generate network transaction ID

Concepts to model:

- Tokenization
- Approval code
- Decline code
- Merchant category code
- Network fees, optional

The agent should never see card numbers.

Use tokens only.

Example:

```text
payment_token = tok_12345
```

Do not store real PAN, CVV, or OTP.

---

## 6.9 Mock Issuer Module

This simulates the bank or card issuer.

Responsibilities:

- Approve or decline payment
- Check simulated OTP/approval
- Apply simple risk rules
- Return issuer response code

Possible issuer rules:

- Decline if amount too high
- Decline if wallet/card token inactive
- Require OTP for certain transactions
- Auto-approve small transactions
- Require manual approval for high-risk transactions

For agent-friendly payments:

- Small payments can be auto-approved.
- Medium payments can require simulated OTP.
- Large payments can require human approval.

This should be simulated internally.

No real SMS/email OTP in initial version.

---

## 6.10 Webhook and Event Module

Payment systems often communicate asynchronously.

Use events for:

- Payment authorized
- Payment captured
- Payment declined
- Payment refunded
- Wallet top-up completed
- Reconciliation break detected

Suggested event names:

```text
wallet.topup.completed
payment.created
payment.authorized
payment.declined
payment.captured
payment.voided
payment.refunded
reconciliation.break.detected
```

### Outbox Pattern

Do not publish events directly inside the same business transaction if using external brokers.

Instead:

1. Save business entity.
2. Save event to `outbox_event` table in same database transaction.
3. Separate worker reads outbox and publishes event.
4. After successful publish, mark outbox event as published.

This avoids inconsistent state.

### Webhook Delivery

Mock gateway sends webhooks to wallet system.

Webhook requirements:

- HMAC signature
- Timestamp
- Event ID
- Retry support
- Idempotent processing
- Replay protection

Example webhook payload:

```json
{
  "event_id": "evt_123",
  "event_type": "payment.captured",
  "occurred_at": "2026-01-01T10:00:00Z",
  "data": {
    "gateway_payment_id": "gw_456",
    "wallet_payment_id": "pay_789",
    "amount_minor_units": 30000,
    "currency": "INR",
    "status": "CAPTURED"
  }
}
```

---

## 6.11 Reconciliation Module

Reconciliation verifies that internal records match gateway records.

### Inputs

Internal data:

- Payments
- Refunds
- Fees
- Ledger entries

Mock gateway settlement report:

- Gateway payment ID
- Merchant ID
- Amount
- Fee
- Net amount
- Status
- Settlement date

### Reconciliation Output

For each record, determine:

```text
MATCHED
MISSING_IN_GATEWAY
MISSING_IN_LEDGER
AMOUNT_MISMATCH
STATUS_MISMATCH
FEE_MISMATCH
```

### Reconciliation Batch Entity

Store:

- Batch ID
- Settlement date
- Started at
- Completed at
- Total records
- Matched records
- Break count
- Status

### Break Entity

Store:

- Break ID
- Batch ID
- Payment ID
- Break type
- Expected amount
- Actual amount
- Resolution status
- Notes

Reconciliation can be scheduled daily.

---

## 6.12 Audit Module

Every important action must be logged.

Audit events include:

- Wallet created
- Wallet funded
- Wallet frozen
- Payment created
- Policy approved
- Policy declined
- Manual approval granted
- Manual approval rejected
- Refund initiated
- Refund completed
- Webhook received
- Reconciliation break detected

Audit log fields:

```text
audit_id
actor_type
actor_id
action
entity_type
entity_id
request_id
idempotency_key
status
reason
metadata
created_at
```

Audit logs must be append-only.

---

# 7. High-Level Data Model

The coding agent can refine these, but the following entities should exist.

## 7.1 Wallet

```text
Wallet
- id
- owner_id
- currency
- status
- created_at
- updated_at
- version
```

## 7.2 Wallet Balance Cache

Optional, but useful.

```text
WalletBalance
- wallet_id
- ledger_balance_minor_units
- available_balance_minor_units
- currency
- updated_at
- version
```

Important:

- This is a cache.
- Ledger remains source of truth.

## 7.3 Ledger Account

```text
LedgerAccount
- id
- account_code
- account_name
- account_type
- currency
- status
- created_at
```

Examples:

- `WALLET_LIABILITY_AGENT_1`
- `SETTLEMENT_ASSET`
- `MERCHANT_PAYABLE_FOODAPP`
- `FEE_INCOME`

## 7.4 Journal Entry

```text
JournalEntry
- id
- transaction_ref
- wallet_id
- payment_id
- description
- entry_date
- created_at
```

A journal entry contains multiple journal lines.

## 7.5 Journal Line

```text
JournalLine
- id
- journal_entry_id
- account_id
- debit_minor_units
- credit_minor_units
- currency
- created_at
```

Rules:

- A line should have either debit or credit, not both.
- Sum of debits equals sum of credits per journal entry.
- Rows are immutable.

## 7.6 Payment Transaction

```text
PaymentTransaction
- id
- wallet_id
- merchant_id
- order_id
- amount_minor_units
- currency
- status
- purpose
- idempotency_key
- request_id
- policy_evaluation_id
- authorization_id
- gateway_payment_id
- network_transaction_id
- created_at
- updated_at
- version
```

## 7.7 Authorization Hold

```text
AuthorizationHold
- id
- payment_id
- wallet_id
- amount_minor_units
- status
- expires_at
- created_at
- updated_at
```

Hold states:

```text
ACTIVE
RELEASED
CAPTURED
EXPIRED
```

## 7.8 Merchant

```text
Merchant
- id
- name
- merchant_category_code
- status
- created_at
```

## 7.9 Merchant Allowlist

```text
MerchantAllowlist
- id
- wallet_id
- merchant_id
- active
- created_at
```

## 7.10 Policy Rule

```text
PolicyRule
- id
- wallet_id
- rule_type
- threshold_value
- window
- status
- created_at
```

Example rule types:

```text
MAX_TRANSACTION_AMOUNT
DAILY_SPEND_LIMIT
MONTHLY_SPEND_LIMIT
CATEGORY_BUDGET
VELOCITY_LIMIT
MANUAL_APPROVAL_THRESHOLD
```

## 7.11 Policy Evaluation

```text
PolicyEvaluation
- id
- payment_id
- wallet_id
- merchant_id
- amount_minor_units
- result
- decline_reason
- evaluated_at
```

## 7.12 Approval Request

```text
ApprovalRequest
- id
- payment_id
- wallet_id
- status
- requested_at
- approved_by
- decided_at
- reason
```

Approval states:

```text
PENDING
APPROVED
REJECTED
EXPIRED
```

## 7.13 Outbox Event

```text
OutboxEvent
- id
- aggregate_type
- aggregate_id
- event_type
- payload
- status
- created_at
- published_at
- retry_count
```

Status:

```text
PENDING
PUBLISHED
FAILED
```

## 7.14 Webhook Event

```text
ReceivedWebhook
- id
- source
- event_id
- event_type
- signature_valid
- processed
- payload
- received_at
```

## 7.15 Reconciliation Batch

```text
ReconciliationBatch
- id
- settlement_date
- status
- total_records
- matched_records
- break_count
- started_at
- completed_at
```

## 7.16 Reconciliation Break

```text
ReconciliationBreak
- id
- batch_id
- payment_id
- break_type
- expected_amount_minor_units
- actual_amount_minor_units
- status
- notes
- created_at
```

## 7.17 Audit Log

```text
AuditLog
- id
- actor_type
- actor_id
- action
- entity_type
- entity_id
- request_id
- idempotency_key
- status
- reason
- metadata
- created_at
```

---

# 8. Core API Design

Use REST first.

All mutating APIs must support idempotency.

Suggested header:

```text
Idempotency-Key: unique-client-generated-key
```

Also include:

```text
X-Request-Id
```

---

## 8.1 Wallet APIs

### Create Wallet

```text
POST /v1/wallets
```

Request:

```json
{
  "owner_id": "user_123",
  "currency": "INR"
}
```

Response:

```json
{
  "wallet_id": "wal_123",
  "status": "ACTIVE",
  "currency": "INR"
}
```

### Get Wallet Balance

```text
GET /v1/wallets/{walletId}/balance
```

Response:

```json
{
  "wallet_id": "wal_123",
  "currency": "INR",
  "ledger_balance_minor_units": 50000,
  "active_holds_minor_units": 10000,
  "available_balance_minor_units": 40000
}
```

### Top-Up Wallet

For sandbox only.

```text
POST /v1/wallets/{walletId}/topups
```

Request:

```json
{
  "amount_minor_units": 50000,
  "currency": "INR",
  "source": "SANDBOX"
}
```

Headers:

```text
Idempotency-Key: topup-123
```

### Freeze Wallet

```text
POST /v1/wallets/{walletId}/freeze
```

### Unfreeze Wallet

```text
POST /v1/wallets/{walletId}/unfreeze
```

### Get Wallet Transactions

```text
GET /v1/wallets/{walletId}/transactions
```

Support filters:

- from_date
- to_date
- payment_id
- merchant_id
- status
- pagination

---

## 8.2 Payment APIs

### Create Payment Authorization

```text
POST /v1/payments/authorizations
```

Request:

```json
{
  "wallet_id": "wal_123",
  "merchant_id": "merchant_foodapp",
  "order_id": "order_456",
  "amount_minor_units": 30000,
  "currency": "INR",
  "purpose": "FOOD_DELIVERY",
  "category": "FOOD"
}
```

Headers:

```text
Idempotency-Key: pay-auth-abc123
```

Possible responses:

```json
{
  "payment_id": "pay_789",
  "status": "AUTHORIZED",
  "amount_minor_units": 30000,
  "authorization_expires_at": "2026-01-01T10:15:00Z"
}
```

Or:

```json
{
  "payment_id": "pay_789",
  "status": "POLICY_DECLINED",
  "decline_reason": "DAILY_LIMIT_EXCEEDED"
}
```

Or:

```json
{
  "payment_id": "pay_789",
  "status": "PENDING_APPROVAL",
  "approval_request_id": "appr_123"
}
```

### Capture Payment

```text
POST /v1/payments/{paymentId}/capture
```

Request:

```json
{
  "amount_minor_units": 30000
}
```

Headers:

```text
Idempotency-Key: pay-capture-abc123
```

### Void Payment

```text
POST /v1/payments/{paymentId}/void
```

### Refund Payment

```text
POST /v1/payments/{paymentId}/refunds
```

Request:

```json
{
  "amount_minor_units": 30000,
  "reason": "ORDER_CANCELLED"
}
```

Headers:

```text
Idempotency-Key: pay-refund-abc123
```

### Get Payment

```text
GET /v1/payments/{paymentId}
```

---

## 8.3 Policy APIs

Admin or user APIs to configure rules.

### Create Policy Rule

```text
POST /v1/wallets/{walletId}/policies
```

Example request:

```json
{
  "rule_type": "DAILY_SPEND_LIMIT",
  "threshold_minor_units": 200000,
  "currency": "INR"
}
```

Another example:

```json
{
  "rule_type": "MAX_TRANSACTION_AMOUNT",
  "threshold_minor_units": 100000,
  "currency": "INR"
}
```

Another example:

```json
{
  "rule_type": "MANUAL_APPROVAL_THRESHOLD",
  "threshold_minor_units": 100000,
  "currency": "INR"
}
```

### Allow Merchant

```text
POST /v1/wallets/{walletId}/allowed-merchants
```

Request:

```json
{
  "merchant_id": "merchant_foodapp"
}
```

---

## 8.4 Approval APIs

### Get Pending Approvals

```text
GET /v1/approvals?status=PENDING
```

### Approve Payment

```text
POST /v1/approvals/{approvalId}/approve
```

### Reject Payment

```text
POST /v1/approvals/{approvalId}/reject
```

---

## 8.5 Merchant APIs

Simple mock merchant APIs.

### List Products

```text
GET /v1/merchants/{merchantId}/products
```

### Create Order

```text
POST /v1/merchants/{merchantId}/orders
```

Request:

```json
{
  "items": [
    {
      "product_id": "prod_1",
      "quantity": 2
    }
  ]
}
```

### Get Order

```text
GET /v1/merchants/{merchantId}/orders/{orderId}
```

---

## 8.6 Gateway Webhook API

Wallet system receives webhooks from mock gateway.

```text
POST /v1/webhooks/gateway
```

Headers:

```text
X-Webhook-Signature: hmac-signature
X-Webhook-Timestamp: timestamp
```

Wallet system must:

- Validate signature
- Store raw webhook
- Check duplicate event ID
- Process idempotently
- Return 200 after safe acceptance

---

# 9. Key Transaction Flows

## 9.1 Wallet Top-Up Flow

1. Admin/user calls top-up API.
2. System validates request.
3. Idempotency key is checked.
4. Wallet must be active.
5. Create top-up record.
6. Write double-entry ledger entries.
7. Update cached balance if used.
8. Emit `wallet.topup.completed`.
9. Return success.

Important:

- Top-up must be idempotent.
- Same idempotency key must return same result without double-crediting.

---

## 9.2 Payment Authorization Flow

1. Agent calls authorize payment API.
2. System validates request.
3. Idempotency key is checked.
4. Payment record created in `CREATED` state.
5. Policy engine evaluates:
   - Wallet active
   - Merchant allowed
   - Amount allowed
   - Balance sufficient
   - Limits not exceeded
   - Approval threshold
6. If policy declines:
   - Payment marked `POLICY_DECLINED`.
   - Return decline reason.
7. If approval required:
   - Create approval request.
   - Payment marked `PENDING_APPROVAL`.
   - Return pending approval response.
8. If approved:
   - Create authorization hold.
   - Reduce available balance logically.
   - Call mock gateway.
9. Mock gateway calls mock network.
10. Mock network calls mock issuer.
11. Mock issuer approves/declines.
12. If approved:
   - Payment marked `AUTHORIZED`.
   - Authorization expiry set.
   - Emit `payment.authorized`.
13. If declined:
   - Release hold if created.
   - Payment marked `AUTHORIZATION_FAILED` or `DECLINED`.
   - Emit `payment.declined`.

---

## 9.3 Payment Capture Flow

1. Merchant or orchestrator calls capture API.
2. System validates payment state.
3. Payment must be `AUTHORIZED` and not expired.
4. Capture amount must be <= authorized amount.
5. Idempotency key checked.
6. Call mock gateway capture.
7. Gateway confirms capture.
8. Update payment to `CAPTURED`.
9. Convert hold to final movement.
10. Write ledger entries.
11. Emit `payment.captured`.
12. Return success.

If capture fails:

- Keep payment in appropriate failed state.
- Do not create final ledger entries.
- Allow retry if idempotent and safe.

---

## 9.4 Payment Void Flow

1. Payment must be `AUTHORIZED`.
2. Payment must not be captured.
3. Idempotency key checked.
4. Call mock gateway void if needed.
5. Release hold.
6. Mark payment `VOIDED`.
7. Emit `payment.voided`.

No final merchant payable entry should remain.

---

## 9.5 Refund Flow

1. Payment must be `CAPTURED`, `PARTIALLY_REFUNDED`, or refundable state.
2. Refund amount must be valid.
3. Idempotency key checked.
4. Create refund record.
5. Call mock gateway refund.
6. Write refund ledger entries.
7. Update payment:
   - `PARTIALLY_REFUNDED` if partial
   - `REFUNDED` if full
8. Emit `payment.refunded`.

Rules:

- Multiple partial refunds allowed.
- Total refunded amount cannot exceed captured amount.

---

## 9.6 Approval Flow

1. Payment crosses approval threshold.
2. Policy engine returns `PENDING_APPROVAL`.
3. Approval request created.
4. Agent/user is notified.
5. Human approves or rejects.
6. If approved:
   - Continue authorization flow.
7. If rejected:
   - Payment marked `POLICY_DECLINED` or `REJECTED`.
   - No hold should remain.
8. Approval request expires if not acted on within timeout.

---

# 10. Concurrency and Consistency Requirements

This project must demonstrate safe handling of concurrent payments.

## 10.1 Double Spend Prevention

The system must prevent:

- Spending more than available balance
- Processing same payment twice
- Capturing more than authorized
- Refunding more than captured

## 10.2 Idempotency

All mutating operations must support idempotency.

Examples:

- Top-up
- Authorization
- Capture
- Void
- Refund
- Webhook processing

Store idempotency records:

```text
IdempotencyRecord
- id
- idempotency_key
- endpoint
- request_hash
- response_status
- response_body
- entity_id
- created_at
- expires_at
```

Rules:

- Same idempotency key returns original response.
- Do not process same request twice.
- Use unique constraints on idempotency key plus endpoint/scope.

## 10.3 Locking Strategy

Use database-level safety.

Possible approach:

- Lock wallet row during balance-affecting operations.
- Use optimistic locking with version column where suitable.
- Use pessimistic locking selectively for critical balance operations.
- Avoid long-held locks.

Recommended:

- Use `SELECT ... FOR UPDATE` only inside short database transactions.
- Or use optimistic locking plus retry for conflict cases.

The implementation should be explicit and tested.

## 10.4 Balance Check

Before authorization:

```text
available_balance >= requested_amount
```

Where:

```text
available_balance = ledger_balance - active_holds
```

The check and hold creation should happen atomically.

---

# 11. Security Requirements

## 11.1 Authentication

Use scoped credentials.

For simplicity:

- JWT token
- API key for agent

For stronger learning:

- OAuth2 resource server
- Scopes

## 11.2 Authorization

Agent token should only allow permitted actions.

Example:

```text
agent scope = wallet:read, payment:create, payment:read
```

Admin token required for:

- Policy changes
- Wallet freeze
- Manual top-up
- Reconciliation operations

## 11.3 No Sensitive Data

Do not store:

- Real card numbers
- CVV
- Real OTP
- Real bank credentials

Use tokens:

```text
payment_token
gateway_token
```

## 11.4 Webhook Security

Each webhook must have:

- HMAC signature
- Timestamp
- Event ID
- Replay protection

Validation rules:

- Reject invalid signature.
- Reject too-old timestamp.
- Reject duplicate event ID.
- Process idempotently.

## 11.5 Auditability

All critical operations must be logged.

Audit log should include:

- Who performed action
- What action occurred
- Which entity was affected
- Why it succeeded or failed
- Request ID
- Idempotency key

---

# 12. Observability Requirements

Add basic observability.

## Metrics

Suggested metrics:

- Payment requests count
- Payment approvals count
- Payment declines count
- Gateway timeout count
- Webhook delivery failures
- Outbox pending events
- Reconciliation breaks
- Ledger imbalance count
- API latency

## Logging

Use structured logs.

Include:

- request_id
- wallet_id
- payment_id
- merchant_id
- trace_id
- status

Do not log secrets.

## Health Checks

Expose:

- Application health
- Database health
- Worker health
- Outbox backlog health

---

# 13. Testing Requirements

Testing is a core part of this project.

## 13.1 Unit Tests

Test:

- Ledger balanced entries
- Payment state machine transitions
- Policy rule evaluation
- Refund amount validation
- Idempotency logic
- Balance calculation

## 13.2 Integration Tests

Use Testcontainers with PostgreSQL.

Test:

- Wallet top-up
- Authorization success
- Authorization decline
- Capture success
- Capture after expiry fails
- Void releases hold
- Refund full and partial
- Duplicate idempotency key
- Webhook duplicate processing
- Reconciliation match and break

## 13.3 Concurrency Tests

Very important.

Example test:

- Wallet has ₹500.
- Fire 1000 concurrent authorization requests of ₹10 each.
- Only 50 should succeed.
- Balance must never become negative.
- No duplicate transactions should be created.

Another test:

- Fire same authorization request concurrently with same idempotency key.
- Only one payment should be created.
- All requests should return consistent result.

## 13.4 Chaos Tests

Simulate:

- Gateway timeout
- Gateway 500 error
- Webhook delayed delivery
- Duplicate webhook
- Capture after gateway failure
- Refund after gateway failure

System should remain consistent.

---

# 14. Recommended Implementation Phases

The coding agent should implement in this order.

---

## Phase 0: Project Setup

Deliverables:

- Spring Boot 3 project
- Java 21
- PostgreSQL with Flyway
- Docker Compose
- OpenAPI/Swagger
- Basic health checks
- Testcontainers setup
- Package structure

Suggested package structure:

```text
com.example.agentwallet
  api
    rest
    dto
    webhook
  domain
    wallet
    ledger
    payment
    policy
    merchant
    reconciliation
    audit
  application
    service
    event
  infrastructure
    persistence
    security
    config
    outbox
    scheduler
```

Definition of done:

- App starts with Docker Compose.
- Database migrations run.
- Swagger UI available.
- Basic test suite passes.

---

## Phase 1: Ledger Core

Deliverables:

- Ledger account entity
- Journal entry entity
- Journal line entity
- Ledger service
- Balance calculation
- Ledger invariant validation
- Append-only behavior

Requirements:

- No journal line can be updated or deleted.
- Every journal entry must balance.
- Ledger entries must be linked to transaction references.

Definition of done:

- Can create balanced ledger entries.
- Can calculate account balance.
- Attempted unbalanced entry fails.
- Unit tests prove ledger correctness.

---

## Phase 2: Wallet and Top-Up

Deliverables:

- Wallet entity
- Wallet status
- Wallet balance cache, optional
- Top-up API
- Top-up ledger integration
- Idempotency for top-up
- Wallet transaction history

Definition of done:

- Wallet can be created.
- Wallet can be funded.
- Balance reflects ledger.
- Duplicate top-up with same idempotency key does not double-credit.
- Wallet transaction history works.

---

## Phase 3: Holds and Available Balance

Deliverables:

- Authorization hold entity
- Available balance calculation
- Hold creation
- Hold release
- Hold expiry

Definition of done:

- Active hold reduces available balance.
- Released hold restores available balance.
- Expired hold restores available balance.
- Tests cover hold lifecycle.

---

## Phase 4: Payment Authorization

Deliverables:

- Payment entity
- Payment state machine
- Authorization API
- Policy integration placeholder
- Mock gateway integration placeholder
- Idempotent authorization

Definition of done:

- Payment can be created.
- Payment can be authorized.
- Payment can be declined.
- Duplicate authorization with same idempotency key is safe.
- Payment states are valid and tested.

---

## Phase 5: Capture, Void, Refund

Deliverables:

- Capture API
- Void API
- Refund API
- Partial refund support
- Ledger entries for capture/refund
- Payment state transitions
- Idempotent capture/void/refund

Definition of done:

- Authorized payment can be captured.
- Captured payment can be refunded.
- Partial refunds work.
- Cannot refund more than captured.
- Cannot capture expired authorization.
- Ledger remains balanced.

---

## Phase 6: Policy Engine

Deliverables:

- Policy rule entity
- Merchant allowlist
- Daily limit
- Monthly limit
- Category budget
- Velocity rule
- Manual approval threshold
- Policy evaluation result
- Policy evaluation audit record

Definition of done:

- Disallowed merchant is declined.
- Transaction above max amount is declined.
- Daily limit is enforced.
- Monthly limit is enforced.
- Category budget is enforced.
- Approval threshold creates pending approval.
- All declines have clear reason codes.

---

## Phase 7: Mock Payment Ecosystem

Deliverables:

- Mock merchant module
- Mock gateway module
- Mock card network module
- Mock issuer module
- Simulated OTP/approval
- Gateway payment ID
- Network transaction ID
- Issuer response codes
- Configurable success/failure behavior

Definition of done:

- Authorization request flows through gateway/network/issuer.
- Gateway returns success/decline/timeout.
- OTP simulation can be auto-approved or manually approved.
- No real card data is used.
- Mock ecosystem is deterministic for tests.

---

## Phase 8: Events, Outbox, Webhooks

Deliverables:

- Outbox table
- Event publisher worker
- Domain events
- Webhook receiver
- HMAC webhook validation
- Duplicate webhook protection
- Retry mechanism
- Dead-letter handling

Definition of done:

- Payment events are stored in outbox.
- Events are published after commit.
- Duplicate webhooks do not cause duplicate processing.
- Invalid webhook signature is rejected.
- Failed events can be retried safely.

---

## Phase 9: Reconciliation

Deliverables:

- Mock settlement report generator
- Reconciliation job
- Reconciliation batch
- Break detection
- Break report API

Definition of done:

- Matched payments are identified.
- Missing records are detected.
- Amount mismatches are detected.
- Status mismatches are detected.
- Reconciliation result is stored.
- Breaks can be queried.

---

## Phase 10: Security and Audit

Deliverables:

- JWT/API-key authentication
- Role-based authorization
- Scoped agent access
- Audit logging
- Request ID propagation
- Idempotency audit linkage

Definition of done:

- Agent cannot modify policy.
- Agent cannot top-up wallet unless allowed.
- Admin can configure rules.
- All critical operations are audited.
- Unauthorized access returns proper errors.

---

## Phase 11: Agent Interface

Deliverables:

- Agent-friendly payment API
- Optional MCP/tool wrapper
- Spend mandate object
- Approval request integration

Example mandate:

```json
{
  "wallet_id": "wal_123",
  "purpose": "FOOD_DELIVERY",
  "merchant_id": "merchant_foodapp",
  "max_amount_minor_units": 60000,
  "currency": "INR",
  "category": "FOOD",
  "expires_at": "2026-01-01T12:00:00Z"
}
```

Definition of done:

- Agent can request payment using mandate.
- Agent cannot exceed mandate.
- Agent receives clear status and decline reason.
- Human approval flow works.

---

## Phase 12: Load and Safety Testing

Deliverables:

- Concurrency tests
- Idempotency tests
- Double-spend prevention tests
- Webhook replay tests
- Gateway failure tests
- Reconciliation tests

Definition of done:

- System remains consistent under concurrent load.
- Balance never goes negative.
- Duplicate requests do not create duplicate money movement.
- Test report demonstrates safety.

---

# 15. Important Domain Rules

The implementation must enforce these rules.

## Money Rules

1. Amounts must be stored as integer minor units.
   - Example: ₹100 = 10000 paise.
2. Never use floating point numbers for money.
3. Currency must always be explicit.
4. Single-currency initial version: INR.

## Ledger Rules

1. Ledger entries are immutable.
2. Corrections require reversal entries.
3. Every journal entry must balance.
4. Ledger is source of truth.
5. Cached balances must be reconcilable.

## Payment Rules

1. Authorization reduces available balance, not necessarily ledger balance.
2. Capture finalizes the payment.
3. Void cancels an authorization.
4. Refund only happens after capture.
5. Refund amount cannot exceed captured amount minus previous refunds.
6. Every state transition must be valid and logged.

## Policy Rules

1. Policy check happens before money movement.
2. Declines must have explicit reason codes.
3. Allowlist is deny-by-default.
4. Limit calculations must consider pending approvals and active holds.
5. Policy changes must be audited.

## Idempotency Rules

1. Every mutating API must support idempotency.
2. Same idempotency key returns same result.
3. Duplicate webhooks are ignored safely.
4. Duplicate event processing must not cause duplicate ledger entries.

---

# 16. Suggested Error Model

Use consistent API errors.

Example:

```json
{
  "error_code": "INSUFFICIENT_BALANCE",
  "message": "Wallet does not have enough available balance.",
  "request_id": "req_123",
  "details": {
    "wallet_id": "wal_123",
    "requested_amount_minor_units": 60000,
    "available_amount_minor_units": 40000
  }
}
```

Common error codes:

```text
WALLET_NOT_FOUND
WALLET_FROZEN
MERCHANT_NOT_ALLOWED
INSUFFICIENT_BALANCE
TRANSACTION_LIMIT_EXCEEDED
DAILY_LIMIT_EXCEEDED
MONTHLY_LIMIT_EXCEEDED
CATEGORY_BUDGET_EXCEEDED
VELOCITY_LIMIT_EXCEEDED
MANUAL_APPROVAL_REQUIRED
PAYMENT_NOT_FOUND
PAYMENT_NOT_AUTHORIZED
PAYMENT_ALREADY_CAPTURED
PAYMENT_EXPIRED
REFUND_EXCEEDS_CAPTURED_AMOUNT
DUPLICATE_IDEMPOTENCY_KEY
INVALID_WEBHOOK_SIGNATURE
RECONCILIATION_BREAK_DETECTED
```

---

# 17. Suggested Configuration

Use environment variables or application config.

Examples:

```text
APP_BASE_URL
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
JWT_SECRET
WEBHOOK_SIGNING_SECRET
OUTBOX_POLL_INTERVAL_MS
AUTHORIZATION_TTL_SECONDS
RECONCILIATION_CRON
MOCK_GATEWAY_LATENCY_MS
MOCK_GATEWAY_FAILURE_RATE
```

Do not hardcode secrets.

---

# 18. Suggested Tech Stack

Use:

- Java 21
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Spring Security
- Spring Validation
- PostgreSQL
- Flyway
- Docker Compose
- Testcontainers
- OpenAPI / Swagger UI
- Micrometer
- Redis, optional for idempotency/rate limiting
- Kafka, optional; initially Spring Events plus outbox is acceptable

Recommended initial approach:

- Use Spring Events + outbox table first.
- Add Kafka later if needed.

This keeps the system simpler while still teaching reliable event delivery.

---

# 19. Definition of Done for the Full Project

The project can be considered complete when:

1. A wallet can be created and funded.
2. An agent can request payment using a scoped token.
3. The policy engine approves or declines correctly.
4. The system prevents spending beyond available balance.
5. The payment lifecycle supports:
   - Authorization
   - Capture
   - Void
   - Refund
6. Ledger entries are double-entry, balanced, and immutable.
7. Idempotency prevents duplicate money movement.
8. Concurrent payments do not cause double spend.
9. Mock gateway simulates real payment behavior.
10. Webhooks are signed, retried, and processed idempotently.
11. Reconciliation detects mismatches.
12. Audit logs record critical actions.
13. OpenAPI documentation is available.
14. Tests prove core safety properties.
15. The system never stores real card details or real OTPs.

---

# 20. Coding Agent Instructions

The implementation agent should follow these instructions:

1. Start with a modular Spring Boot monolith.
2. Do not create microservices initially.
3. Use PostgreSQL for all persistent data.
4. Use Flyway for schema migrations.
5. Use integer minor units for all money amounts.
6. Implement ledger before payment gateway features.
7. Keep ledger append-only.
8. Add idempotency before building complex flows.
9. Simulate all external payment systems locally.
10. Do not integrate real banks, real cards, or real merchant websites.
11. Write integration tests using Testcontainers.
12. Add concurrency tests early.
13. Ensure every payment state transition is explicit.
14. Ensure every decline has a reason code.
15. Ensure webhook processing is idempotent.
16. Ensure reconciliation can detect mismatches.
17. Keep code modular and testable.
18. Prefer clear domain names over generic names.
19. Document APIs using OpenAPI.
20. Maintain a safe sandbox-only mindset.

---

# 21. Final Deliverable

The final output should be a working Java Spring Boot application that demonstrates:

- Safe agent spending
- Double-entry accounting
- Payment authorization and settlement
- Policy-based controls
- Reliable asynchronous processing
- Reconciliation
- Auditability
- Concurrency safety

This project should be suitable for learning backend payment systems and demonstrating payment-platform thinking in backend engineering interviews.