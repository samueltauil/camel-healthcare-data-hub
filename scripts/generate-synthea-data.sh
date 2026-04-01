#!/usr/bin/env bash
# ============================================================
# generate-synthea-data.sh
#
# Downloads Synthea and generates synthetic healthcare data
# in CSV and FHIR formats for the camel-data-layer demo.
#
# Usage:
#   ./scripts/generate-synthea-data.sh [population_size] [state]
#
# Examples:
#   ./scripts/generate-synthea-data.sh          # 20 patients, Massachusetts
#   ./scripts/generate-synthea-data.sh 50 Texas  # 50 patients, Texas
#   ./scripts/generate-synthea-data.sh --force   # regenerate even if data exists
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

SYNTHEA_VERSION="3.3.0"
SYNTHEA_JAR="synthea-with-dependencies.jar"
SYNTHEA_DIR="$PROJECT_DIR/.synthea"
SYNTHEA_OUTPUT="$SYNTHEA_DIR/output"

SAMPLE_DATA_DIR="$PROJECT_DIR/sample-data"
FTP_SEED_DIR="$PROJECT_DIR/sample-data/ftp-seed"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }

# Handle --force flag
FORCE=false
POSITIONAL=()
for arg in "$@"; do
    case "$arg" in
        --force|-f) FORCE=true ;;
        *) POSITIONAL+=("$arg") ;;
    esac
done

POPULATION="${POSITIONAL[0]:-20}"
STATE="${POSITIONAL[1]:-Massachusetts}"

# -----------------------------------------------------------
# 0. Check for existing data
# -----------------------------------------------------------
if [[ "$FORCE" == false && -d "$FTP_SEED_DIR" && -n "$(ls -A "$FTP_SEED_DIR" 2>/dev/null)" ]]; then
    seed_count=$(ls "$FTP_SEED_DIR" 2>/dev/null | wc -l)
    info "Synthea data already exists ($seed_count files in ftp-seed/)."
    info "Skipping generation. Use --force to regenerate."
    info ""
    info "  FTP seed dir:  $FTP_SEED_DIR/"
    info "  To upload:     ./scripts/seed-ftp.sh"
    exit 0
fi

# -----------------------------------------------------------
# 1. Download Synthea if not present
# -----------------------------------------------------------
mkdir -p "$SYNTHEA_DIR"

if [[ ! -f "$SYNTHEA_DIR/$SYNTHEA_JAR" ]]; then
    info "Downloading Synthea v${SYNTHEA_VERSION}..."
    curl -sL \
        "https://github.com/synthetichealth/synthea/releases/download/v${SYNTHEA_VERSION}/${SYNTHEA_JAR}" \
        -o "$SYNTHEA_DIR/$SYNTHEA_JAR"
    info "Synthea downloaded to $SYNTHEA_DIR/$SYNTHEA_JAR"
else
    info "Synthea already present at $SYNTHEA_DIR/$SYNTHEA_JAR"
fi

# -----------------------------------------------------------
# 2. Clean previous Synthea output to avoid stale data
# -----------------------------------------------------------
if [[ -d "$SYNTHEA_OUTPUT" ]]; then
    info "Cleaning previous Synthea output..."
    rm -rf "$SYNTHEA_OUTPUT"
fi

# -----------------------------------------------------------
# 3. Run Synthea to generate data
# -----------------------------------------------------------
info "Generating $POPULATION synthetic patients in $STATE..."

cd "$SYNTHEA_DIR"

java -jar "$SYNTHEA_JAR" \
    --exporter.csv.export=true \
    --exporter.fhir.export=true \
    --exporter.hospital.fhir.export=false \
    --exporter.practitioner.fhir.export=false \
    --exporter.ccda.export=false \
    --exporter.hl7.export=true \
    --generate.append_numbers_to_person_names=false \
    -p "$POPULATION" \
    -s 42 \
    "$STATE"

# -----------------------------------------------------------
# 4. Copy generated data into project sample-data
# -----------------------------------------------------------
info "Copying generated data to sample-data/..."

# Clean previous sample data
rm -rf "$SAMPLE_DATA_DIR/csv/synthea" "$SAMPLE_DATA_DIR/fhir/synthea" "$SAMPLE_DATA_DIR/hl7/synthea" "$FTP_SEED_DIR"

# CSV files
mkdir -p "$SAMPLE_DATA_DIR/csv/synthea"
if [[ -d "$SYNTHEA_OUTPUT/csv" ]]; then
    cp "$SYNTHEA_OUTPUT/csv/patients.csv"      "$SAMPLE_DATA_DIR/csv/synthea/" 2>/dev/null || true
    cp "$SYNTHEA_OUTPUT/csv/observations.csv"   "$SAMPLE_DATA_DIR/csv/synthea/" 2>/dev/null || true
    cp "$SYNTHEA_OUTPUT/csv/conditions.csv"     "$SAMPLE_DATA_DIR/csv/synthea/" 2>/dev/null || true
    cp "$SYNTHEA_OUTPUT/csv/medications.csv"    "$SAMPLE_DATA_DIR/csv/synthea/" 2>/dev/null || true
    cp "$SYNTHEA_OUTPUT/csv/allergies.csv"      "$SAMPLE_DATA_DIR/csv/synthea/" 2>/dev/null || true
    cp "$SYNTHEA_OUTPUT/csv/procedures.csv"     "$SAMPLE_DATA_DIR/csv/synthea/" 2>/dev/null || true
    cp "$SYNTHEA_OUTPUT/csv/encounters.csv"     "$SAMPLE_DATA_DIR/csv/synthea/" 2>/dev/null || true
    cp "$SYNTHEA_OUTPUT/csv/immunizations.csv"  "$SAMPLE_DATA_DIR/csv/synthea/" 2>/dev/null || true
    info "  CSV files copied: $(ls "$SAMPLE_DATA_DIR/csv/synthea/" | wc -l) files"
fi

# FHIR bundles
mkdir -p "$SAMPLE_DATA_DIR/fhir/synthea"
if [[ -d "$SYNTHEA_OUTPUT/fhir" ]]; then
    cp "$SYNTHEA_OUTPUT/fhir/"*.json "$SAMPLE_DATA_DIR/fhir/synthea/" 2>/dev/null || true
    info "  FHIR bundles copied: $(ls "$SAMPLE_DATA_DIR/fhir/synthea/"*.json 2>/dev/null | wc -l) files"
fi

# HL7 messages
mkdir -p "$SAMPLE_DATA_DIR/hl7/synthea"
if [[ -d "$SYNTHEA_OUTPUT/hl7" ]]; then
    cp "$SYNTHEA_OUTPUT/hl7/"*.hl7 "$SAMPLE_DATA_DIR/hl7/synthea/" 2>/dev/null || true
    info "  HL7 messages copied: $(ls "$SAMPLE_DATA_DIR/hl7/synthea/"*.hl7 2>/dev/null | wc -l) files"
fi

# -----------------------------------------------------------
# 5. Create FTP seed directory (files to upload to FTP)
# -----------------------------------------------------------
mkdir -p "$FTP_SEED_DIR"

# CSV files — only patients.csv has a matching parser (SyntheaPatient model)
# Prefix with "synthea-" so the Camel router uses the Synthea CSV parser
if [[ -f "$SAMPLE_DATA_DIR/csv/synthea/patients.csv" ]]; then
    cp "$SAMPLE_DATA_DIR/csv/synthea/patients.csv" "$FTP_SEED_DIR/synthea-patients.csv"
fi

# HL7 files — copy up to 10
for f in $(ls "$SAMPLE_DATA_DIR/hl7/synthea/"*.hl7 2>/dev/null | head -10); do
    cp "$f" "$FTP_SEED_DIR/"
done

seed_count=$(ls "$FTP_SEED_DIR" 2>/dev/null | wc -l)

info ""
info "=== Generation Complete ==="
info "  Population:    $POPULATION patients"
info "  CSV files:     $SAMPLE_DATA_DIR/csv/synthea/"
info "  FHIR bundles:  $SAMPLE_DATA_DIR/fhir/synthea/"
info "  HL7 messages:  $SAMPLE_DATA_DIR/hl7/synthea/"
info "  FTP seed dir:  $FTP_SEED_DIR/ ($seed_count files ready)"
info ""
info "To upload seed data to FTP:"
info "  ./scripts/seed-ftp.sh"
