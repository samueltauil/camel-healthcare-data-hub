package com.healthcare.datalayer.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Normalized domain model representing a patient record.
 *
 * <p>This is the canonical patient representation used throughout the data layer.
 * It is produced by multiple ingestion paths:
 * <ul>
 *   <li>Unmarshalled directly from CSV via Camel Bindy on the {@code direct:process-csv} route</li>
 *   <li>Converted from {@link SyntheaPatient} via {@link SyntheaPatient#toDomainPatient()}</li>
 *   <li>Parsed from HL7 v2 PID segments by {@code Hl7MessageProcessor}</li>
 * </ul>
 *
 * <p>Once created, patients are stored in the in-memory {@code patientStore} and included in
 * {@link ClinicalDocument} instances that are published to JMS, Kafka, FHIR, and SOAP endpoints.
 * The REST API also serves patients directly from the store.
 *
 * <p>Equality is based solely on {@link #patientId}.
 */
@CsvRecord(separator = ",", skipFirstLine = true)
public class Patient {

    // CSV column 1: unique patient identifier
    @DataField(pos = 1)
    private String patientId;

    // CSV column 2: patient first/given name
    @DataField(pos = 2)
    private String firstName;

    // CSV column 3: patient last/family name
    @DataField(pos = 3)
    private String lastName;

    // CSV column 4: date of birth (ISO format yyyy-MM-dd)
    @DataField(pos = 4, pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    // CSV column 5: gender code (e.g. "M", "F")
    @DataField(pos = 5)
    private String gender;

    // CSV column 6: Social Security Number
    @DataField(pos = 6)
    private String ssn;

    // CSV column 7: street address line 1
    @DataField(pos = 7)
    private String addressLine1;

    // CSV column 8: city name
    @DataField(pos = 8)
    private String city;

    // CSV column 9: state abbreviation
    @DataField(pos = 9)
    private String state;

    // CSV column 10: ZIP/postal code
    @DataField(pos = 10)
    private String zipCode;

    // CSV column 11: phone number
    @DataField(pos = 11)
    private String phoneNumber;

    // CSV column 12: email address
    @DataField(pos = 12)
    private String email;

    // CSV column 13: insurance/plan identifier
    @DataField(pos = 13)
    private String insuranceId;

    // CSV column 14: name of the primary care provider
    @DataField(pos = 14)
    private String primaryCareProvider;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime ingestedAt;

    private String sourceFile;

    public Patient() {
        this.ingestedAt = LocalDateTime.now();
    }

    // --- Getters & Setters ---

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getSsn() { return ssn; }
    public void setSsn(String ssn) { this.ssn = ssn; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getInsuranceId() { return insuranceId; }
    public void setInsuranceId(String insuranceId) { this.insuranceId = insuranceId; }

    public String getPrimaryCareProvider() { return primaryCareProvider; }
    public void setPrimaryCareProvider(String primaryCareProvider) { this.primaryCareProvider = primaryCareProvider; }

    public LocalDateTime getIngestedAt() { return ingestedAt; }
    public void setIngestedAt(LocalDateTime ingestedAt) { this.ingestedAt = ingestedAt; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    /**
     * Returns the patient's full name by concatenating first and last name.
     *
     * @return the full name in "{@code firstName lastName}" format
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Patient patient = (Patient) o;
        return Objects.equals(patientId, patient.patientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patientId);
    }

    @Override
    public String toString() {
        return "Patient{id='%s', name='%s %s', dob=%s, gender='%s'}"
                .formatted(patientId, firstName, lastName, dateOfBirth, gender);
    }
}
