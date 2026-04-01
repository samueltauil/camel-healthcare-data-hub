# Camel Data Layer — Healthcare Flat-File Integration Hub

A Java 21 / Quarkus project using Apache Camel to ingest flat files (CSV, HL7v2) from an FTP server and route them to multiple healthcare-standard output connectors.

## Architecture

```mermaid
flowchart TD
    FTP["🗂️ FTP Server\n(CSV / HL7v2 files)"]
    ROUTER{"Content-Based Router\n(by file extension)"}
    CSV["CSV Parser\n(Bindy)"]
    SYNTHEA["Synthea CSV Parser"]
    HL7["HL7v2 Parser\n(HAPI)"]
    MODEL["Normalized Model\n(Patient, Observation, ClinicalDocument)"]

    FTP --> ROUTER
    ROUTER -->|.csv| CSV
    ROUTER -->|synthea-*.csv| SYNTHEA
    ROUTER -->|.hl7| HL7

    CSV --> MODEL
    SYNTHEA --> MODEL
    HL7 --> MODEL

    MODEL --> REST["🌐 REST API\n(Platform HTTP)"]
    MODEL --> SOAP["📄 SOAP\n(CXF / WSDL)"]
    MODEL --> MLLP["🏥 HL7 MLLP\n(TCP)"]
    MODEL --> FHIR["🔥 FHIR R4\n(HAPI FHIR)"]
    MODEL --> JMS["📨 JMS\n(ActiveMQ Artemis)"]
    MODEL --> KAFKA["📡 Kafka\n(Event Streaming)"]
```

## Infrastructure

```mermaid
graph LR
    subgraph Docker Compose
        FTP_SRV["Pure-FTPd\n:21"]
        AMQ["ActiveMQ Artemis\n:61616 / :8161"]
        KFK["Kafka (KRaft)\n:9092"]
        FHIR_SRV["HAPI FHIR Server\n:8090"]
        MLLP_RCV["MLLP Receiver\n:2575"]
    end

    APP["Camel Data Layer\n:8080"] --> FTP_SRV
    APP --> AMQ
    APP --> KFK
    APP --> FHIR_SRV
    APP --> MLLP_RCV
```

## Output Connectors

| Connector | Protocol | Endpoint |
|-----------|----------|----------|
| REST API | HTTP/JSON | `GET /api/patients`, `GET /api/observations`, `GET /api/health` |
| SOAP | XML/WSDL | `/soap/PatientService` — `getPatient`, `searchPatients`, `getAllPatients` |
| HL7 MLLP | TCP | Outbound HL7v2 messages to `mllp://host:2575` |
| FHIR R4 | HTTP/JSON | POST FHIR Bundle to HAPI FHIR Server |
| JMS | AMQP | `queue.patients`, `topic.clinical-events` on ActiveMQ Artemis |
| Kafka | TCP | `healthcare.patients.ingested` topic |

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose (for infrastructure)

## Quick Start

### 1. Start infrastructure

```bash
docker-compose up -d
```

This starts:
- **FTP Server** (Pure-FTPd) on port 21
- **ActiveMQ Artemis** on port 61616 (console: http://localhost:8161)
- **Kafka** (KRaft) on port 9092
- **HAPI FHIR Server** on port 8090 (UI: http://localhost:8090)
- **MLLP Receiver** (socat) on port 2575

### 2. Generate synthetic data with Synthea

Uses [Synthea](https://github.com/synthetichealth/synthea) to create realistic synthetic patients:

```bash
chmod +x scripts/*.sh

# Generate 20 patients (default)
./scripts/generate-synthea-data.sh

# Or generate 100 patients in Texas
./scripts/generate-synthea-data.sh 100 Texas
```

This downloads Synthea, generates CSV/HL7/FHIR data, and places it in `sample-data/`.

### 3. Build & run the application

```bash
mvn quarkus:dev
```

### 4. Seed the FTP server with data

```bash
# Upload Synthea-generated files
./scripts/seed-ftp.sh

# Or upload individual files manually
curl -T sample-data/csv/patients.csv ftp://localhost/inbox/ --user healthcare:healthcare123
curl -T sample-data/hl7/adt-a01.hl7 ftp://localhost/inbox/ --user healthcare:healthcare123
```

### 5. Query the REST API

```bash
# Health check
curl http://localhost:8080/api/health

# List all patients
curl http://localhost:8080/api/patients

# Get a specific patient
curl http://localhost:8080/api/patients/P001

# List all documents
curl http://localhost:8080/api/documents
```

### 6. Check the SOAP endpoint

```bash
# WSDL
curl http://localhost:8080/soap/PatientService?wsdl

# SOAP request
curl -X POST http://localhost:8080/soap/PatientService \
  -H "Content-Type: text/xml" \
  -d '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:soap="http://healthcare.com/datalayer/soap">
    <soapenv:Body>
      <soap:getPatient>
        <patientId>P001</patientId>
      </soap:getPatient>
    </soapenv:Body>
  </soapenv:Envelope>'
```

## Project Structure

```mermaid
graph LR
    subgraph "src/main/java/com/healthcare/datalayer/"
        CONFIG["config/\nAppConfig, ObjectMapper, stores"]
        MODEL["model/\nPatient, Observation,\nClinicalDocument, SyntheaPatient"]
        PROC["processor/\nCSV · HL7 · FHIR · SOAP\n· Synthea processors"]
        ROUTE["route/\nFTP · REST · SOAP · MLLP\n· FHIR · JMS · Kafka"]
        SOAP_PKG["soap/\nPatientService interface\n& implementation"]
    end

    CONFIG --- MODEL
    MODEL --- PROC
    PROC --- ROUTE
    ROUTE --- SOAP_PKG
```

## Configuration

All settings are in `src/main/resources/application.properties`. Key properties:

| Property | Default | Description |
|----------|---------|-------------|
| `ftp.host` | localhost | FTP server hostname |
| `ftp.poll.delay` | 5000 | Polling interval (ms) |
| `mllp.host` / `mllp.port` | localhost:2575 | HL7 MLLP target |
| `fhir.server.url` | http://localhost:8090/fhir | FHIR R4 server |
| `kafka.bootstrap.servers` | localhost:9092 | Kafka brokers |

## Running Tests

```bash
mvn test
```

## Building for Production

```bash
# JVM build
mvn package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar

# Docker build
docker build -t camel-data-layer .
docker run -p 8080:8080 camel-data-layer
```

## Sample Data

### Hand-crafted (checked in)
- `sample-data/csv/patients.csv` — 10 patient records
- `sample-data/hl7/adt-a01.hl7` — ADT^A01 admission message

### Synthea-generated (gitignored, generated on demand)
- `sample-data/csv/synthea/` — Synthea CSV exports (patients, observations, conditions, etc.)
- `sample-data/fhir/synthea/` — FHIR R4 bundles per patient
- `sample-data/hl7/synthea/` — HL7v2 messages
- `sample-data/ftp-seed/` — Pre-selected files ready for FTP upload

Files prefixed with `synthea-` are auto-detected and parsed using the Synthea CSV schema.

## Technology Stack

- **Java 21** + **Quarkus** runtime
- **Apache Camel 4.x** (camel-quarkus extensions)
- **HAPI HL7v2** for HL7 message parsing
- **HAPI FHIR R4** for FHIR resource building
- **Quarkus CXF** for SOAP/WSDL
- **ActiveMQ Artemis** for JMS messaging
- **Apache Kafka** for event streaming
