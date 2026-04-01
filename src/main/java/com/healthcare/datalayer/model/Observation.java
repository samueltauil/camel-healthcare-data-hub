package com.healthcare.datalayer.model;

import java.time.LocalDateTime;
import java.util.Objects;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Represents a clinical observation (lab result, vital sign, etc.) linked to a patient.
 *
 * <p>Annotated as a Camel Bindy CSV record for potential unmarshalling from observation CSV files.
 * Observations are included in {@link ClinicalDocument} instances and are converted to FHIR
 * Observation resources by {@code FhirBundleProcessor}. They are also served via the REST API
 * from the in-memory {@code observationStore}.
 *
 * <p>The coding fields ({@link #code}, {@link #codeSystem}, {@link #displayName}) follow
 * standard terminology conventions (e.g. LOINC, SNOMED CT).
 *
 * <p>Equality is based solely on {@link #observationId}.
 */
@CsvRecord(separator = ",", skipFirstLine = true)
public class Observation {

    // CSV column 1: unique observation identifier
    @DataField(pos = 1)
    private String observationId;

    // CSV column 2: ID of the patient this observation belongs to
    @DataField(pos = 2)
    private String patientId;

    // CSV column 3: coded value (e.g. LOINC code)
    @DataField(pos = 3)
    private String code;

    // CSV column 4: coding system URI (e.g. "http://loinc.org")
    @DataField(pos = 4)
    private String codeSystem;

    // CSV column 5: human-readable name for the code
    @DataField(pos = 5)
    private String displayName;

    // CSV column 6: observed value (numeric or textual)
    @DataField(pos = 6)
    private String value;

    // CSV column 7: unit of measure (e.g. "mg/dL", "mmHg")
    @DataField(pos = 7)
    private String unit;

    // CSV column 8: observation status (e.g. "final", "preliminary")
    @DataField(pos = 8)
    private String status;

    // CSV column 9: observation category (e.g. "vital-signs", "laboratory")
    @DataField(pos = 9)
    private String category;

    // CSV column 10: date/time the observation was clinically effective
    @DataField(pos = 10, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime effectiveDateTime;

    // CSV column 11: name of the practitioner who performed the observation
    @DataField(pos = 11)
    private String performerName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime ingestedAt;

    private String sourceFile;

    public Observation() {
        this.ingestedAt = LocalDateTime.now();
    }

    // --- Getters & Setters ---

    public String getObservationId() { return observationId; }
    public void setObservationId(String observationId) { this.observationId = observationId; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCodeSystem() { return codeSystem; }
    public void setCodeSystem(String codeSystem) { this.codeSystem = codeSystem; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDateTime getEffectiveDateTime() { return effectiveDateTime; }
    public void setEffectiveDateTime(LocalDateTime effectiveDateTime) { this.effectiveDateTime = effectiveDateTime; }

    public String getPerformerName() { return performerName; }
    public void setPerformerName(String performerName) { this.performerName = performerName; }

    public LocalDateTime getIngestedAt() { return ingestedAt; }
    public void setIngestedAt(LocalDateTime ingestedAt) { this.ingestedAt = ingestedAt; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Observation that = (Observation) o;
        return Objects.equals(observationId, that.observationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(observationId);
    }

    @Override
    public String toString() {
        return "Observation{id='%s', patient='%s', code='%s', value='%s %s'}"
                .formatted(observationId, patientId, code, value, unit);
    }
}
