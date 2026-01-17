#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG_DIR="$ROOT_DIR/config"

DEVICE_ID="${DEVICE_ID:-device-123}"
OWNER="${OWNER:-operator-xyz}"
TIMESTAMP="${TIMESTAMP:-$(date -u +"%Y-%m-%dT%H:%M:%SZ")}"
FIRMWARE_HASH="${FIRMWARE_HASH:-sha256:deadbeef}"
BUILD_ID="${BUILD_ID:-yocto-2026-01-17}"
RECIPE="${RECIPE:-core-image-minimal}"
BOARD_REV="${BOARD_REV:-rev-a}"
NONCE="${NONCE:-}"

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

MESSAGE="$DEVICE_ID|$OWNER|$TIMESTAMP|$FIRMWARE_HASH|$BUILD_ID|$RECIPE|$BOARD_REV|$NONCE"
SIGNATURE=$(printf '%s' "$MESSAGE" | openssl dgst -sha256 -sign "$PRIVATE_KEY" | base64 | tr -d '\n')

cat <<PAYLOAD
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
