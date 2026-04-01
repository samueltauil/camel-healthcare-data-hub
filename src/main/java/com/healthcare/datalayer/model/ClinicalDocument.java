package com.healthcare.datalayer.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Aggregate wrapper that groups {@link Patient} and {@link Observation} records
 * originating from a single ingested source file.
 *
 * <p>A {@code ClinicalDocument} is assembled by each ingestion processor
 * ({@code CsvPatientProcessor}, {@code SyntheaCsvProcessor}, {@code Hl7MessageProcessor})
 * and stored in the in-memory {@code documentStore}. After creation it is fanned out to
 * multiple downstream Camel routes:
 * <ul>
 *   <li>{@code seda:to-jms} &mdash; marshalled to JSON and published to a JMS queue</li>
 *   <li>{@code seda:to-kafka} &mdash; marshalled to JSON and published to a Kafka topic</li>
 *   <li>{@code seda:to-fhir} &mdash; converted to a FHIR Bundle by {@code FhirBundleProcessor}</li>
 *   <li>{@code seda:to-mllp} &mdash; forwarded to an MLLP outbound route</li>
 * </ul>
 *
 * <p>The REST API also returns documents directly from the store.
 */
public class ClinicalDocument {

    private String documentId;
    private String sourceFile;
    private String sourceFormat;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime ingestedAt;

    private List<Patient> patients = new ArrayList<>();
    private List<Observation> observations = new ArrayList<>();

    public ClinicalDocument() {
        this.ingestedAt = LocalDateTime.now();
    }

    public ClinicalDocument(String documentId, String sourceFile, String sourceFormat) {
        this();
        this.documentId = documentId;
        this.sourceFile = sourceFile;
        this.sourceFormat = sourceFormat;
    }

    // --- Getters & Setters ---

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    public String getSourceFormat() { return sourceFormat; }
    public void setSourceFormat(String sourceFormat) { this.sourceFormat = sourceFormat; }

    public LocalDateTime getIngestedAt() { return ingestedAt; }
    public void setIngestedAt(LocalDateTime ingestedAt) { this.ingestedAt = ingestedAt; }

    public List<Patient> getPatients() { return patients; }
    public void setPatients(List<Patient> patients) { this.patients = patients; }

    public List<Observation> getObservations() { return observations; }
    public void setObservations(List<Observation> observations) { this.observations = observations; }

    /** Appends a patient to this document's patient list. */
    public void addPatient(Patient patient) { this.patients.add(patient); }

    /** Appends an observation to this document's observation list. */
    public void addObservation(Observation observation) { this.observations.add(observation); }

    @Override
    public String toString() {
        return "ClinicalDocument{id='%s', source='%s', format='%s', patients=%d, observations=%d}"
                .formatted(documentId, sourceFile, sourceFormat, patients.size(), observations.size());
    }
}
