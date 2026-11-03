"""Load and validate local MCP gateway configuration."""

from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse
from uuid import UUID

TOOL_NAMES = frozenset(
    {
        "read_wallet",
        "list_transactions",
        "list_holds",
        "release_hold",
        "request_payment",
        "read_payment",
        "capture_payment",
        "void_payment",
    }
)


@dataclass(frozen=True)
class WalletAccess:
    wallet_id: str
    api_key: str
    tools: frozenset[str]


@dataclass(frozen=True)
class AgentConfig:
    agent_id: str
    token_hash: str
    wallets: dict[str, WalletAccess]


def load_allowlist(path: Path) -> dict[str, AgentConfig]:
    """Read agent token hashes and wallet-scoped API credentials."""
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"Cannot read MCP agent allowlist at {path}.") from exc
    if not isinstance(raw, dict) or not isinstance(raw.get("agents"), list):
        raise RuntimeError("MCP allowlist must contain an 'agents' array.")

    agents: dict[str, AgentConfig] = {}
    agent_ids: set[str] = set()
    for entry in raw["agents"]:
        if not isinstance(entry, dict):
            raise RuntimeError("Each MCP allowlist agent must be an object.")
        agent_id = entry.get("agent_id")
        token_hash = entry.get("token_sha256", "")
        if not isinstance(agent_id, str) or not re.fullmatch(r"[A-Za-z0-9._-]{1,80}", agent_id):
            raise RuntimeError("Each allowlisted agent needs a simple, unique agent_id.")
        if not isinstance(token_hash, str) or not re.fullmatch(r"[a-fA-F0-9]{64}", token_hash):
            raise RuntimeError(f"Agent {agent_id} needs a 64-character SHA-256 token hash.")
        token_hash = token_hash.lower()
        if token_hash in agents or agent_id in agent_ids:
            raise RuntimeError("MCP allowlist agent IDs and token hashes must be unique.")
        agent_ids.add(agent_id)
        wallets = _load_wallet_access(agent_id, entry.get("wallets"))
        agents[token_hash] = AgentConfig(agent_id, token_hash, wallets)
    return agents


def _load_wallet_access(agent_id: str, entries: object) -> dict[str, WalletAccess]:
    if not isinstance(entries, list) or not entries:
        raise RuntimeError(f"Agent {agent_id} must be assigned at least one wallet.")

    wallets: dict[str, WalletAccess] = {}
    for entry in entries:
        if not isinstance(entry, dict):
            raise RuntimeError(f"Agent {agent_id} wallet entries must be objects.")
        try:
            wallet_id = str(UUID(entry["wallet_id"]))
        except (KeyError, ValueError, TypeError) as exc:
            raise RuntimeError(f"Agent {agent_id} has an invalid wallet_id.") from exc

        key_env = entry.get("wallet_api_key_env")
        api_key = os.getenv(key_env, "") if isinstance(key_env, str) else ""
        if not isinstance(key_env, str) or not key_env or not api_key:
            raise RuntimeError(f"Set the wallet API key environment variable for agent {agent_id}.")

        tools = entry.get("tools")
        if (
            not isinstance(tools, list)
            or not tools
            or any(not isinstance(tool, str) or tool not in TOOL_NAMES for tool in tools)
        ):
            raise RuntimeError(f"Agent {agent_id} has an empty or unsupported tools list.")
        if wallet_id in wallets:
            raise RuntimeError(f"Agent {agent_id} lists wallet {wallet_id} more than once.")
        wallets[wallet_id] = WalletAccess(wallet_id, api_key, frozenset(tools))
    return wallets


def wallet_api_base_url() -> str:
    value = os.getenv("WALLET_API_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise RuntimeError("WALLET_API_BASE_URL must be an HTTP or HTTPS URL.")
    local_hosts = {"localhost", "127.0.0.1", "::1"}
    if parsed.hostname not in local_hosts and parsed.scheme != "https":
        raise RuntimeError("Use HTTPS for remote wallet API connections.")
    return value
