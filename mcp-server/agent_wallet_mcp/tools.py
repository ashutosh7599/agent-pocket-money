"""MCP tool definitions, mapped to fixed Agent Wallet API routes."""

from __future__ import annotations

from typing import Any
from uuid import UUID

from mcp.server.fastmcp import FastMCP

from agent_wallet_mcp.auth import wallet_for_call
from agent_wallet_mcp.wallet_api import call_wallet_api


def create_mcp_server() -> FastMCP:
    mcp = FastMCP(
        "Agent Wallet",
        instructions=(
            "Use only the wallet IDs and operations granted to the authenticated agent. "
            "Spending mandates and wallet policies are enforced by the wallet API. "
            "Never ask the caller to provide or reveal a wallet API key."
        ),
    )

    @mcp.tool()
    async def read_wallet(wallet_id: str) -> dict[str, Any]:
        """Read this agent's allowed wallet balance and status."""
        access = wallet_for_call(wallet_id, "read_wallet")
        return await call_wallet_api(access, "GET", f"/api/v1/wallets/{access.wallet_id}")

    @mcp.tool()
    async def list_transactions(wallet_id: str, page: int = 0, size: int = 20) -> dict[str, Any]:
        """List ledger transactions for an allowed wallet."""
        if page < 0 or size < 1 or size > 100:
            raise ValueError("page must be non-negative and size must be between 1 and 100.")
        access = wallet_for_call(wallet_id, "list_transactions")
        return await call_wallet_api(
            access,
            "GET",
            f"/api/v1/wallets/{access.wallet_id}/transactions",
            params={"page": page, "size": size},
        )

    @mcp.tool()
    async def list_holds(
        wallet_id: str,
        status: str = "ACTIVE",
        page: int = 0,
        size: int = 20,
    ) -> dict[str, Any]:
        """List active or historical authorization holds for an allowed wallet."""
        if status not in {"ACTIVE", "RELEASED", "EXPIRED", "CAPTURED"}:
            raise ValueError("status must be ACTIVE, RELEASED, EXPIRED, or CAPTURED.")
        if page < 0 or size < 1 or size > 100:
            raise ValueError("page must be non-negative and size must be between 1 and 100.")
        access = wallet_for_call(wallet_id, "list_holds")
        return await call_wallet_api(
            access,
            "GET",
            f"/api/v1/wallets/{access.wallet_id}/holds",
            params={"status": status, "page": page, "size": size},
        )

    @mcp.tool()
    async def release_hold(wallet_id: str, hold_id: str) -> dict[str, Any]:
        """Release an authorization hold on an allowed wallet."""
        access = wallet_for_call(wallet_id, "release_hold")
        normalized_hold_id = str(UUID(hold_id))
        return await call_wallet_api(
            access,
            "POST",
            f"/api/v1/wallets/{access.wallet_id}/holds/{normalized_hold_id}/release",
        )

    @mcp.tool()
    async def request_payment(
        wallet_id: str, mandate_id: str, amount_minor_units: int, idempotency_key: str
    ) -> dict[str, Any]:
        """Request payment under a user-created spend mandate and wallet policy."""
        if amount_minor_units <= 0:
            raise ValueError("amount_minor_units must be positive.")
        validate_idempotency_key(idempotency_key)
        access = wallet_for_call(wallet_id, "request_payment")
        normalized_mandate_id = str(UUID(mandate_id))
        return await call_wallet_api(
            access,
            "POST",
            f"/api/v1/wallets/{access.wallet_id}/mandates/{normalized_mandate_id}/payments",
            body={"amountMinorUnits": amount_minor_units},
            idempotency_key=idempotency_key,
        )

    @mcp.tool()
    async def read_payment(wallet_id: str, payment_id: str) -> dict[str, Any]:
        """Read a payment that belongs to an allowed wallet."""
        access = wallet_for_call(wallet_id, "read_payment")
        normalized_payment_id = str(UUID(payment_id))
        return await call_wallet_api(
            access, "GET", f"/api/v1/wallets/{access.wallet_id}/payments/{normalized_payment_id}"
        )

    @mcp.tool()
    async def capture_payment(
        wallet_id: str, payment_id: str, idempotency_key: str
    ) -> dict[str, Any]:
        """Capture an authorized payment using an idempotency key."""
        validate_idempotency_key(idempotency_key)
        access = wallet_for_call(wallet_id, "capture_payment")
        normalized_payment_id = str(UUID(payment_id))
        return await call_wallet_api(
            access,
            "POST",
            f"/api/v1/wallets/{access.wallet_id}/payments/{normalized_payment_id}/capture",
            idempotency_key=idempotency_key,
        )

    @mcp.tool()
    async def void_payment(
        wallet_id: str, payment_id: str, idempotency_key: str
    ) -> dict[str, Any]:
        """Void an authorized payment and release its hold."""
        validate_idempotency_key(idempotency_key)
        access = wallet_for_call(wallet_id, "void_payment")
        normalized_payment_id = str(UUID(payment_id))
        return await call_wallet_api(
            access,
            "POST",
            f"/api/v1/wallets/{access.wallet_id}/payments/{normalized_payment_id}/void",
            idempotency_key=idempotency_key,
        )

    return mcp


def validate_idempotency_key(key: str) -> None:
    if not key or not key.strip() or len(key) > 128:
        raise ValueError("idempotency_key must contain between 1 and 128 characters.")
