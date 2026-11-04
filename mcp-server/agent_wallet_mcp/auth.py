"""MCP bearer authentication, agent context, and per-agent request limits."""

from __future__ import annotations

import asyncio
import collections
import contextvars
import hashlib
import hmac
import os
import time
from typing import Any
from uuid import UUID

from agent_wallet_mcp.config import AgentConfig, WalletAccess

CURRENT_AGENT: contextvars.ContextVar[AgentConfig | None] = contextvars.ContextVar(
    "agent_wallet_mcp_agent", default=None
)


def current_agent() -> AgentConfig:
    agent = CURRENT_AGENT.get()
    if agent is None:
        raise RuntimeError("This tool requires an authenticated, allowlisted MCP agent.")
    return agent


def wallet_for_call(wallet_id: str, tool_name: str) -> WalletAccess:
    agent = current_agent()
    try:
        normalized_id = str(UUID(wallet_id))
    except (ValueError, TypeError) as exc:
        raise ValueError("wallet_id must be a UUID.") from exc
    access = agent.wallets.get(normalized_id)
    if access is None:
        raise PermissionError("This agent is not allowlisted for that wallet.")
    if tool_name not in access.tools:
        raise PermissionError(f"Agent {agent.agent_id} is not allowed to use {tool_name}.")
    return access


class AgentBearerAllowlist:
    """Reject MCP HTTP requests without an allowlisted bearer token."""

    def __init__(self, app: Any, agents: dict[str, AgentConfig]):
        self.app = app
        self.agents = agents
        self.requests_per_minute = int(os.getenv("MCP_REQUESTS_PER_MINUTE_PER_AGENT", "120"))
        if self.requests_per_minute < 1:
            raise RuntimeError("MCP_REQUESTS_PER_MINUTE_PER_AGENT must be positive.")
        self.request_times: dict[str, collections.deque[float]] = {}
        self.rate_limit_lock = asyncio.Lock()

    async def __call__(self, scope: dict[str, Any], receive: Any, send: Any) -> None:
        if scope["type"] != "http" or scope.get("path") != "/mcp":
            await self.app(scope, receive, send)
            return
        auth_headers = [
            value
            for key, value in scope.get("headers", [])
            if key.lower() == b"authorization"
        ]
        if len(auth_headers) != 1:
            await self._unauthorized(send)
            return
        try:
            scheme, token = auth_headers[0].decode("latin-1").split(" ", 1)
        except ValueError:
            await self._unauthorized(send)
            return
        if scheme.lower() != "bearer" or not token or " " in token:
            await self._unauthorized(send)
            return

        supplied_hash = hashlib.sha256(token.encode("utf-8")).hexdigest()
        agent = None
        for token_hash, configured_agent in self.agents.items():
            if hmac.compare_digest(token_hash, supplied_hash):
                agent = configured_agent
        if agent is None:
            await self._unauthorized(send)
            return
        if not await self._within_rate_limit(agent.agent_id):
            await self._too_many_requests(send)
            return

        context_token = CURRENT_AGENT.set(agent)
        try:
            await self.app(scope, receive, send)
        finally:
            CURRENT_AGENT.reset(context_token)

    async def _within_rate_limit(self, agent_id: str) -> bool:
        now = time.monotonic()
        async with self.rate_limit_lock:
            recent = self.request_times.setdefault(agent_id, collections.deque())
            while recent and recent[0] <= now - 60:
                recent.popleft()
            if len(recent) >= self.requests_per_minute:
                return False
            recent.append(now)
            return True

    @staticmethod
    async def _unauthorized(send: Any) -> None:
        body = b'{"error":"unauthorized","detail":"MCP agent is not allowlisted."}'
        await send(
            {
                "type": "http.response.start",
                "status": 401,
                "headers": [
                    (b"content-type", b"application/json"),
                    (b"content-length", str(len(body)).encode("ascii")),
                ],
            }
        )
        await send({"type": "http.response.body", "body": body})

    @staticmethod
    async def _too_many_requests(send: Any) -> None:
        body = b'{"error":"rate_limited","detail":"Agent request limit reached."}'
        await send(
            {
                "type": "http.response.start",
                "status": 429,
                "headers": [
                    (b"content-type", b"application/json"),
                    (b"content-length", str(len(body)).encode("ascii")),
                    (b"retry-after", b"60"),
                ],
            }
        )
        await send({"type": "http.response.body", "body": body})
