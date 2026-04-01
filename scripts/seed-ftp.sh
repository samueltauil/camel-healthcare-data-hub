#!/usr/bin/env bash
# ============================================================
# seed-ftp.sh
#
# Uploads sample data files to the FTP server for processing
# by the Camel Data Layer application.
#
# Uploads:
#   1. Bundled sample files (patients.csv, adt-a01.hl7) — always
#   2. Synthea-generated files from ftp-seed/ — if available
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

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info() { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $*"; }

upload_file() {
    local file="$1"
    local filename="$(basename "$file")"
    info "  Uploading: $filename"
    curl -T "$file" "ftp://$FTP_HOST/$FTP_DIR/$filename" \
        --user "$FTP_USER:$FTP_PASS" \
        --silent --show-error
    count=$((count + 1))
}

info "Uploading sample data to ftp://$FTP_HOST/$FTP_DIR/ ..."
echo ""

count=0

# 1. Bundled sample files (always available)
info "— Bundled sample data —"

if [[ -f "$PROJECT_DIR/sample-data/csv/patients.csv" ]]; then
    upload_file "$PROJECT_DIR/sample-data/csv/patients.csv"
fi

if [[ -f "$PROJECT_DIR/sample-data/hl7/adt-a01.hl7" ]]; then
    upload_file "$PROJECT_DIR/sample-data/hl7/adt-a01.hl7"
fi

# 2. Synthea-generated files (if generate-synthea-data.sh has been run)
SEED_DIR="$PROJECT_DIR/sample-data/ftp-seed"

if [[ -d "$SEED_DIR" ]] && [[ -n "$(ls -A "$SEED_DIR" 2>/dev/null)" ]]; then
    echo ""
    info "— Synthea-generated data —"
    for file in "$SEED_DIR"/*; do
        if [[ -f "$file" ]]; then
            upload_file "$file"
        fi
    done
else
    echo ""
    warn "No Synthea data found. Run ./scripts/generate-synthea-data.sh to generate more."
fi

echo ""
info "Uploaded $count file(s) to ftp://$FTP_HOST/$FTP_DIR/"
info "The Camel FTP poller will pick them up automatically."
