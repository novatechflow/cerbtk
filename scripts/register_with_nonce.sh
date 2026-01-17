#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG_DIR="$ROOT_DIR/config"

BASE_URL="${BASE_URL:-http://localhost:23230}"
DEVICE_ID="${DEVICE_ID:-device-123}"
OWNER="${OWNER:-operator-xyz}"
TIMESTAMP="${TIMESTAMP:-$(date -u +"%Y-%m-%dT%H:%M:%SZ")}"
FIRMWARE_HASH="${FIRMWARE_HASH:-sha256:deadbeef}"
BUILD_ID="${BUILD_ID:-yocto-2026-01-17}"
RECIPE="${RECIPE:-core-image-minimal}"
BOARD_REV="${BOARD_REV:-rev-a}"

mkdir -p "$CONFIG_DIR"

PRIVATE_KEY="$CONFIG_DIR/sample_private.pem"
if [[ ! -f "$PRIVATE_KEY" ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$PRIVATE_KEY" > /dev/null
fi

PUB_KEY=$(openssl pkey -in "$PRIVATE_KEY" -pubout -outform DER | base64 | tr -d '\n')
{
  echo "# Sample key for demo payload in config/sample_private.pem"
  echo "$PUB_KEY"
} > "$CONFIG_DIR/trusted_keys.txt"

NONCE_JSON=$(curl -sS "$BASE_URL/device/nonce/$DEVICE_ID")
NONCE=$(python - <<'PY'
import json, sys
try:
    data = json.loads(sys.stdin.read())
    print(data.get("nonce", ""))
except Exception:
    print("")
PY
<<< "$NONCE_JSON")

if [[ -z "$NONCE" ]]; then
  echo "Failed to obtain nonce from $BASE_URL" >&2
  echo "$NONCE_JSON" >&2
  exit 1
fi

MESSAGE="$DEVICE_ID|$OWNER|$TIMESTAMP|$FIRMWARE_HASH|$BUILD_ID|$RECIPE|$BOARD_REV|$NONCE"
SIGNATURE=$(printf '%s' "$MESSAGE" | openssl dgst -sha256 -sign "$PRIVATE_KEY" | base64 | tr -d '\n')

PAYLOAD=$(cat <<PAYLOAD
{
  "deviceId": "$DEVICE_ID",
  "owner": "$OWNER",
  "timestamp": "$TIMESTAMP",
  "publicKey": "$PUB_KEY",
  "signature": "$SIGNATURE",
  "firmwareHash": "$FIRMWARE_HASH",
  "buildId": "$BUILD_ID",
  "recipe": "$RECIPE",
  "boardRev": "$BOARD_REV",
  "nonce": "$NONCE",
  "algorithm": "SHA256withRSA"
}
PAYLOAD
)

curl -sS -X POST "$BASE_URL/device/write" \
  -H 'Content-Type: application/json' \
  --data "$PAYLOAD"
