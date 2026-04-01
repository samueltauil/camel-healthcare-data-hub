#!/usr/bin/env bash
# ============================================================
# seed-ftp.sh
#
# Uploads generated Synthea data files to the FTP server
# for processing by the Camel Data Layer application.
#
# Usage:
#   ./scripts/seed-ftp.sh [ftp_host] [ftp_user] [ftp_pass]
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

FTP_HOST="${1:-localhost}"
FTP_USER="${2:-healthcare}"
FTP_PASS="${3:-healthcare123}"
FTP_DIR="inbox"

SEED_DIR="$PROJECT_DIR/sample-data/ftp-seed"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

info() { echo -e "${GREEN}[INFO]${NC}  $*"; }
err()  { echo -e "${RED}[ERR]${NC}   $*"; }

if [[ ! -d "$SEED_DIR" ]] || [[ -z "$(ls -A "$SEED_DIR" 2>/dev/null)" ]]; then
    err "No seed data found in $SEED_DIR"
    err "Run ./scripts/generate-synthea-data.sh first"
    exit 1
fi

info "Uploading seed data to ftp://$FTP_HOST/$FTP_DIR/ ..."

count=0
for file in "$SEED_DIR"/*; do
    if [[ -f "$file" ]]; then
        filename="$(basename "$file")"
        info "  Uploading: $filename"
        curl -T "$file" "ftp://$FTP_HOST/$FTP_DIR/$filename" \
            --user "$FTP_USER:$FTP_PASS" \
            --silent --show-error
        count=$((count + 1))
    fi
done

info ""
info "Uploaded $count file(s) to ftp://$FTP_HOST/$FTP_DIR/"
info "The Camel FTP poller will pick them up automatically."
