#!/usr/bin/env bash
# Load jobra-backend/.env/local.env (Bash)
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env/local.env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE — copy .env/local.env.example to .env/local.env" >&2
  exit 1
fi
set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a
echo "Loaded environment from .env/local.env"
