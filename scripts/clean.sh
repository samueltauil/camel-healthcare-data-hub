#!/usr/bin/env bash
# ============================================================
# clean.sh
#
# Resets the entire environment for a fresh demo:
#   - Stops and removes all Docker containers and volumes
#   - Clears Maven build artifacts
#   - Removes generated Synthea data
#   - Removes FTP seed directory
#
# Usage:
#   ./scripts/clean.sh
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
step()  { echo -e "${GREEN}▸${NC} $*"; }

echo ""
echo -e "${GREEN}=== Camel Healthcare Data Hub — Clean Reset ===${NC}"
echo ""

# ----------------------------------------------------------
# 1. Stop Docker Compose stack and remove volumes
# ----------------------------------------------------------
if [[ -f "$PROJECT_DIR/docker-compose.yml" ]]; then
    step "Stopping Docker containers and removing volumes..."
    cd "$PROJECT_DIR"
    docker-compose down -v --remove-orphans 2>/dev/null || warn "docker-compose not available or already stopped"
fi

# ----------------------------------------------------------
# 2. Clean Maven build artifacts
# ----------------------------------------------------------
step "Removing Maven build artifacts (target/)..."
rm -rf "$PROJECT_DIR/target"

# ----------------------------------------------------------
# 3. Remove generated Synthea data
# ----------------------------------------------------------
step "Removing Synthea runtime and generated data..."
rm -rf "$PROJECT_DIR/.synthea"
rm -rf "$PROJECT_DIR/sample-data/csv/synthea"
rm -rf "$PROJECT_DIR/sample-data/fhir/synthea"
rm -rf "$PROJECT_DIR/sample-data/hl7/synthea"
rm -rf "$PROJECT_DIR/sample-data/ftp-seed"

# ----------------------------------------------------------
# 4. Remove any FTP processed-file markers
# ----------------------------------------------------------
step "Removing FTP processed-file markers (.done, .error)..."
find "$PROJECT_DIR/sample-data" -type d -name ".done" -exec rm -rf {} + 2>/dev/null || true
find "$PROJECT_DIR/sample-data" -type d -name ".error" -exec rm -rf {} + 2>/dev/null || true

echo ""
info "Environment is clean. To start a fresh demo:"
echo ""
echo "  1. docker-compose up -d"
echo "  2. mvn clean quarkus:dev"
echo "  3. curl -T sample-data/csv/patients.csv ftp://localhost/inbox/ --user healthcare:healthcare123"
echo "  4. curl http://localhost:8080/api/patients"
echo ""
