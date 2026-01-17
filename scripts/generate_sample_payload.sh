#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG_DIR="$ROOT_DIR/config"

mkdir -p "$CONFIG_DIR"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$CONFIG_DIR/sample_private.pem"
openssl pkey -in "$CONFIG_DIR/sample_private.pem" -pubout -outform DER | base64 > "$CONFIG_DIR/sample_public.der.b64"

NONCE="demo-nonce"
MESSAGE="device-123|operator-xyz|2026-01-17T10:00:00Z|sha256:deadbeef|yocto-2026-01-17|core-image-minimal|rev-a|$NONCE"
printf '%s' "$MESSAGE" > "$CONFIG_DIR/sample_message.txt"

openssl dgst -sha256 -sign "$CONFIG_DIR/sample_private.pem" -out "$CONFIG_DIR/sample_signature.bin" "$CONFIG_DIR/sample_message.txt"
base64 -i "$CONFIG_DIR/sample_signature.bin" -o "$CONFIG_DIR/sample_signature.b64"
rm -f "$CONFIG_DIR/sample_signature.bin"

PUB_KEY=$(tr -d '\n' < "$CONFIG_DIR/sample_public.der.b64")
SIG=$(tr -d '\n' < "$CONFIG_DIR/sample_signature.b64")
cat > "$CONFIG_DIR/sample_payload.json" <<PAYLOAD
{
  "deviceId": "device-123",
  "owner": "operator-xyz",
  "timestamp": "2026-01-17T10:00:00Z",
  "publicKey": "$PUB_KEY",
  "signature": "$SIG",
  "firmwareHash": "sha256:deadbeef",
  "buildId": "yocto-2026-01-17",
  "recipe": "core-image-minimal",
  "boardRev": "rev-a",
  "nonce": "$NONCE",
  "algorithm": "SHA256withRSA"
}
PAYLOAD

{
  echo "# Sample key for demo payload in config/sample_payload.json"
  echo "$PUB_KEY"
} > "$CONFIG_DIR/trusted_keys.txt"

echo "Generated sample payload and keys in $CONFIG_DIR"
