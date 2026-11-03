#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
  echo "Run ./setup.sh first; it creates .env and installs the Python environment." >&2
  exit 1
fi
if [[ ! -x mcp-server/.venv/bin/python ]]; then
  echo "Run ./setup.sh first; the MCP virtual environment is missing." >&2
  exit 1
fi

set -a
# Local .env is shell syntax; use generated URL-safe API keys or quote custom values.
source .env
set +a

if [[ -z "${WALLET_API_KEYS:-}" ]]; then
  echo "WALLET_API_KEYS is empty. Run ./setup.sh again or add a local ADMIN key to .env." >&2
  exit 1
fi

docker compose up -d postgres otel-collector

echo "Starting Agent Wallet API..."
mvn spring-boot:run &
JAVA_PID=$!
MCP_PID=""

cleanup() {
  trap - EXIT INT TERM
  if [[ -n "$MCP_PID" ]]; then kill "$MCP_PID" 2>/dev/null || true; fi
  if [[ -n "$JAVA_PID" ]]; then kill "$JAVA_PID" 2>/dev/null || true; fi
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "Waiting for the API health endpoint..."
ready=false
for attempt in $(seq 1 120); do
  if curl --silent --fail http://127.0.0.1:8080/actuator/health >/dev/null; then
    ready=true
    break
  fi
  if ! kill -0 "$JAVA_PID" 2>/dev/null; then
    echo "The API process exited before becoming healthy." >&2
    exit 1
  fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  echo "API did not become healthy. Check Maven and database output above." >&2
  exit 1
fi

echo "Starting Agent Wallet MCP server..."
(
  cd "$ROOT_DIR/mcp-server"
  MCP_AGENT_ALLOWLIST_FILE="${MCP_AGENT_ALLOWLIST_FILE:-agents.json}" \
    "$ROOT_DIR/mcp-server/.venv/bin/python" server.py
) &
MCP_PID=$!

echo "API: http://127.0.0.1:8080 (Swagger: /swagger-ui.html)"
echo "MCP: http://127.0.0.1:${MCP_PORT:-8001}/mcp"
echo "Press Ctrl+C to stop the API and MCP server."
wait -n "$JAVA_PID" "$MCP_PID"
