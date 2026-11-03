# Agent Wallet MCP server

This Python server exposes a small MCP tool set over Streamable HTTP and forwards requests to the Agent Wallet REST API. It keeps the wallet API credential on the server. Each MCP caller must present a separate bearer token whose SHA-256 hash is in the allowlist. Each allowed agent is restricted to specific wallet IDs and tools.

The wallet credential configured here should be an `AGENT` credential issued by the wallet API for the same wallet. It is not the user's broad `USER` credential. The wallet API then enforces both user ownership and the credential's single-wallet scope, while this server provides a second allowlist boundary for MCP clients.

## Configure

1. Use the wallet API admin credential to create a user, create that user's wallet, and issue an `AGENT` credential scoped to that wallet. Save the returned key as a secret named `MCP_ALICE_WALLET_API_KEY`.
2. Generate an independent MCP bearer token for the trusted agent:

   ```bash
   python3 -c 'import hashlib,secrets; t=secrets.token_urlsafe(32); print("MCP_AGENT_TOKEN="+t); print("TOKEN_SHA256="+hashlib.sha256(t.encode()).hexdigest())'
   ```

   Keep `MCP_AGENT_TOKEN` in the agent host's secret store. Put only `TOKEN_SHA256` into the server's private `agents.json` file.
3. Copy `agents.example.json` to `agents.json`, set the wallet ID, hash, environment variable name, and allowed tool names. The example starts with an empty agent list, so the MCP service denies all MCP requests until you add an entry. `agents.json` and `.env` are ignored by Git.
4. From the repository root, run `./setup.sh` once, then `./run.sh` to start both the Spring API and MCP server. Setup creates the Python environment, installs dependencies, initializes local configuration, and starts PostgreSQL plus the OpenTelemetry Collector. This also works on WSL where Python's `ensurepip` package is absent.

For MCP-only manual setup after the API is already running:

   ```bash
   cd mcp-server
   ../mcp-server/.venv/bin/python -m pip install -r requirements.txt
   export WALLET_API_BASE_URL=http://127.0.0.1:8080
   export MCP_ALICE_WALLET_API_KEY='<wallet-scoped-agent-key>'
   export MCP_AGENT_ALLOWLIST_FILE=agents.json
   python server.py
   ```

   The MCP endpoint is `http://127.0.0.1:8001/mcp`. The default bind is loopback. Remote binding requires `MCP_ALLOW_REMOTE_BIND=true`; put the server behind HTTPS before allowing remote connections.

Configure an MCP client that supports Streamable HTTP headers to connect to that endpoint and send `Authorization: Bearer <MCP_AGENT_TOKEN>`. For example, a client configuration can point its remote MCP server URL at `http://127.0.0.1:8001/mcp` and supply the bearer header from that agent's secret store. The MCP bearer token is different from the wallet API key. Never give the wallet API key to the agent.

This project uses a pre-shared bearer-token allowlist for a simple private deployment. Host the endpoint only on a trusted network and use HTTPS for remote clients. The default request limit is 120 MCP HTTP requests per agent per minute; set `MCP_REQUESTS_PER_MINUTE_PER_AGENT` to change it. For public or enterprise deployment, replace the static token verifier with the company's OAuth 2.1 identity provider; the MCP Python SDK supports a resource-server token verifier for that setup.

## Available tools

- `read_wallet`, `list_transactions`, `list_holds`, `release_hold`
- `request_payment` uses a user-created, one-time spend mandate and the wallet's policy checks.
- `read_payment`, `capture_payment`, and `void_payment`

There are no MCP tools for funding wallets, changing policies, creating mandates, approving payments, or reconciliation. Those operations stay on the wallet admin API. Add only the tools an agent needs to its `tools` list. An unknown token, wallet ID, or tool is rejected before the wallet API is called.

To revoke an MCP agent, remove its allowlist entry and restart the MCP process. To revoke the underlying wallet API credential, use the wallet API admin endpoint; the wallet backend rejects it on the next API request.

## Allowlist format

Each wallet entry includes an environment variable name for the wallet-scoped API credential. This lets one approved agent reach multiple explicitly listed wallets while using a different API credential for each wallet.

```json
{
  "agents": [
    {
      "agent_id": "trusted-shopping-agent",
      "token_sha256": "64-lowercase-hex-characters",
      "wallets": [
        {
          "wallet_id": "wallet-uuid",
          "wallet_api_key_env": "MCP_ALICE_WALLET_API_KEY",
          "tools": ["read_wallet", "request_payment"]
        }
      ]
    }
  ]
}
```

The server checks caller tokens using SHA-256 hashes and constant-time comparison. It does not log bearer tokens or wallet API keys. Use a dedicated MCP token for each agent so access can be removed without rotating other agents' credentials.
