"""Application startup for the Agent Wallet MCP gateway."""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Any

import uvicorn
from mcp.server.fastmcp import FastMCP

from agent_wallet_mcp.auth import AgentBearerAllowlist
from agent_wallet_mcp.config import AgentConfig, load_allowlist
from agent_wallet_mcp.tools import create_mcp_server

LOGGER = logging.getLogger("agent_wallet_mcp")


def create_app(allowlist_path: Path) -> tuple[Any, dict[str, AgentConfig]]:
    agents = load_allowlist(allowlist_path)
    mcp: FastMCP = create_mcp_server()
    return AgentBearerAllowlist(mcp.streamable_http_app(), agents), agents


def main() -> None:
    logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"), format="%(levelname)s %(message)s")
    allowlist_path = Path(os.getenv("MCP_AGENT_ALLOWLIST_FILE", "agents.json"))
    app, agents = create_app(allowlist_path)
    host = os.getenv("MCP_HOST", "127.0.0.1")
    port = int(os.getenv("MCP_PORT", "8001"))
    if host not in {"127.0.0.1", "localhost", "::1"} and os.getenv("MCP_ALLOW_REMOTE_BIND") != "true":
        raise RuntimeError("Remote binding requires MCP_ALLOW_REMOTE_BIND=true and a TLS-terminating proxy.")
    LOGGER.info(
        "Starting Agent Wallet MCP server on %s:%s with %d allowlisted agent(s)",
        host,
        port,
        len(agents),
    )
    if not agents:
        LOGGER.warning("No MCP agents are allowlisted yet; every MCP request will be rejected.")
    uvicorn.run(app, host=host, port=port, access_log=False)
