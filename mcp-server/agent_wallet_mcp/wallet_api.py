"""HTTP client wrapper for the Agent Wallet API."""

from __future__ import annotations

import logging
from typing import Any

import httpx

from agent_wallet_mcp.auth import current_agent
from agent_wallet_mcp.config import WalletAccess, wallet_api_base_url

LOGGER = logging.getLogger("agent_wallet_mcp")


async def call_wallet_api(
    wallet: WalletAccess,
    method: str,
    path: str,
    *,
    params: dict[str, Any] | None = None,
    body: dict[str, Any] | None = None,
    idempotency_key: str | None = None,
) -> dict[str, Any]:
    headers = {"X-API-Key": wallet.api_key, "Accept": "application/json"}
    if idempotency_key:
        headers["Idempotency-Key"] = idempotency_key
    try:
        async with httpx.AsyncClient(base_url=wallet_api_base_url(), timeout=15.0) as client:
            response = await client.request(method, path, headers=headers, params=params, json=body)
    except httpx.RequestError:
        LOGGER.warning("Wallet API request failed for agent %s", current_agent().agent_id)
        return {"ok": False, "error": "Wallet API is unavailable."}

    try:
        payload: Any = response.json() if response.content else None
    except ValueError:
        payload = {"detail": "Wallet API returned a non-JSON response."}
    if not response.is_success:
        return {"ok": False, "status": response.status_code, "error": payload}
    return {"ok": True, "data": payload}
