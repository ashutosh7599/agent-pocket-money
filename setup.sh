#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

fail() { echo "Setup error: $*" >&2; exit 1; }

command -v java >/dev/null || fail "Java 21 is required."
JAVA_VERSION="$(java -version 2>&1 | head -n 1 | sed -E 's/.*version "([0-9]+).*/\1/')"
[[ "$JAVA_VERSION" == "21" ]] || fail "Java 21 is required; found Java ${JAVA_VERSION:-unknown}."
command -v mvn >/dev/null || fail "Maven is required."
command -v python3 >/dev/null || fail "Python 3 is required."
python3 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)' || fail "Python 3.10 or newer is required."
command -v docker >/dev/null || fail "Docker with the Compose plugin is required."
command -v curl >/dev/null || fail "curl is required by run.sh for the startup health check."
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required."

echo "Preparing the local Python MCP environment..."
if [[ ! -x mcp-server/.venv/bin/python ]]; then
  python3 -m venv --without-pip mcp-server/.venv || fail "Python venv support is unavailable."
fi
if ! mcp-server/.venv/bin/python -m pip --version >/dev/null 2>&1; then
  BOOTSTRAP_PIP="$(mktemp)"
  trap 'rm -f "$BOOTSTRAP_PIP"' EXIT
  curl --fail --silent --show-error https://bootstrap.pypa.io/get-pip.py --output "$BOOTSTRAP_PIP"
  mcp-server/.venv/bin/python "$BOOTSTRAP_PIP" --disable-pip-version-check
  rm -f "$BOOTSTRAP_PIP"
  trap - EXIT
fi
mcp-server/.venv/bin/python -m pip install --disable-pip-version-check -r mcp-server/requirements.txt

if [[ ! -f .env ]]; then
  python3 - <<'PY'
from pathlib import Path
import secrets

template = Path(".env.example").read_text(encoding="utf-8")
admin_key = secrets.token_urlsafe(32)
template = template.replace("WALLET_API_KEYS=", f"WALLET_API_KEYS={admin_key}:ADMIN", 1)
Path(".env").write_text(template, encoding="utf-8")
PY
  chmod 600 .env
  echo "Created .env with a random local admin key. Keep this file private."
fi

if [[ ! -f mcp-server/agents.json ]]; then
  cp mcp-server/agents.example.json mcp-server/agents.json
  chmod 600 mcp-server/agents.json
fi

echo "Starting local PostgreSQL and OpenTelemetry Collector..."
docker compose up -d postgres otel-collector
for attempt in $(seq 1 60); do
  if docker compose exec -T postgres pg_isready -U root -d main_db >/dev/null 2>&1; then
    echo "PostgreSQL is ready."
    echo "Setup complete. Start the API and MCP server with ./run.sh"
    echo "Local config: .env and mcp-server/agents.json (both ignored by Git)."
    exit 0
  fi
  sleep 1
done
fail "PostgreSQL did not become ready. Check: docker compose logs postgres"
