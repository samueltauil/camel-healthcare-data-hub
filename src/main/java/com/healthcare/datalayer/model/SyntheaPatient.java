package com.healthcare.datalayer.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Bindy CSV model that maps the Synthea-generated {@code patients.csv} format.
 *
 * <p>Synthea is a synthetic patient generator; its CSV export uses a fixed 27-column layout:
 * <em>Id, BIRTHDATE, DEATHDATE, SSN, DRIVERS, PASSPORT, PREFIX, FIRST, LAST, SUFFIX,
 * MAIDEN, MARITAL, RACE, ETHNICITY, GENDER, BIRTHPLACE, ADDRESS, CITY, STATE, COUNTY,
 * FIPS, ZIP, LAT, LON, HEALTHCARE_EXPENSES, HEALTHCARE_COVERAGE, INCOME</em>.
 *
 * <p>This class is unmarshalled on the {@code direct:process-synthea-csv} Camel route via
 * Bindy and then immediately converted to the normalized {@link Patient} domain model by
 * {@link #toDomainPatient()} inside {@code SyntheaCsvProcessor}. It is not persisted itself.
 *
 * @see Patient
 */
@CsvRecord(separator = ",", skipFirstLine = true)
public class SyntheaPatient {

    // Synthea column 1 (Id): unique patient UUID
    @DataField(pos = 1)
    private String id;

    // Synthea column 2 (BIRTHDATE): date of birth in yyyy-MM-dd
    @DataField(pos = 2, pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    // Synthea column 3 (DEATHDATE): date of death, empty if alive
    @DataField(pos = 3)
    private String deathDate;

    // Synthea column 4 (SSN): Social Security Number
    @DataField(pos = 4)
    private String ssn;

    // Synthea column 5 (DRIVERS): driver's license number
    @DataField(pos = 5)
    private String drivers;

    // Synthea column 6 (PASSPORT): passport number
    @DataField(pos = 6)
    private String passport;

    // Synthea column 7 (PREFIX): name prefix (e.g. "Mr.", "Mrs.")
    @DataField(pos = 7)
    private String prefix;

    // Synthea column 8 (FIRST): first/given name
    @DataField(pos = 8)
    private String firstName;

    // Synthea column 9 (MIDDLE): middle name
    @DataField(pos = 9)
    private String middleName;

    // Synthea column 10 (LAST): last/family name
    @DataField(pos = 10)
    private String lastName;

    // Synthea column 11 (SUFFIX): name suffix (e.g. "Jr.", "III")
    @DataField(pos = 11)
    private String suffix;

    // Synthea column 12 (MAIDEN): maiden name
    @DataField(pos = 12)
    private String maiden;

    // Synthea column 13 (MARITAL): marital status code (e.g. "M", "S")
    @DataField(pos = 13)
    private String marital;

    // Synthea column 14 (RACE): race
    @DataField(pos = 14)
    private String race;

    // Synthea column 15 (ETHNICITY): ethnicity
    @DataField(pos = 15)
    private String ethnicity;

    // Synthea column 16 (GENDER): gender code ("M" or "F")
    @DataField(pos = 16)
    private String gender;

    // Synthea column 17 (BIRTHPLACE): city/state of birth
    @DataField(pos = 17)
    private String birthplace;

    // Synthea column 18 (ADDRESS): street address
    @DataField(pos = 18)
    private String address;

    // Synthea column 19 (CITY): city name
    @DataField(pos = 19)
    private String city;

    // Synthea column 20 (STATE): state abbreviation
    @DataField(pos = 20)
    private String state;

    // Synthea column 21 (COUNTY): county name
    @DataField(pos = 21)
    private String county;

    // Synthea column 22 (FIPS): FIPS county code
    @DataField(pos = 22)
    private String fips;

    // Synthea column 23 (ZIP): ZIP/postal code
    @DataField(pos = 23)
    private String zip;

    // Synthea column 24 (LAT): latitude of patient address
    @DataField(pos = 24)
    private String lat;

    // Synthea column 25 (LON): longitude of patient address
    @DataField(pos = 25)
    private String lon;

    // Synthea column 26 (HEALTHCARE_EXPENSES): total healthcare expenses
    @DataField(pos = 26)
    private String healthcareExpenses;

    // Synthea column 27 (HEALTHCARE_COVERAGE): total healthcare coverage
    @DataField(pos = 27)
    private String healthcareCoverage;

    // Synthea column 28 (INCOME): annual income
    @DataField(pos = 28)
    private String income;

    public SyntheaPatient() {}

    /**
     * Converts this Synthea-format patient record into the normalized {@link Patient} domain model.
     *
     * <p>Maps the Synthea-specific fields to their domain equivalents:
     * {@code id → patientId}, {@code birthDate → dateOfBirth},
     * {@code address → addressLine1}, {@code zip → zipCode}.
     * Gender is normalized to a single uppercase letter ({@code "M"} or {@code "F"}).
     * The {@link Patient#getIngestedAt() ingestedAt} timestamp is set to the current time.
     *
     * @return a new {@link Patient} populated from this Synthea record
     */
    public Patient toDomainPatient() {
        Patient patient = new Patient();
        patient.setPatientId(id);
        patient.setFirstName(firstName);
        patient.setLastName(lastName);
        patient.setDateOfBirth(birthDate);
        patient.setSsn(ssn);
        patient.setAddressLine1(address);
        patient.setCity(city);
        patient.setState(state);
        patient.setZipCode(zip);
        patient.setIngestedAt(LocalDateTime.now());

        if (gender != null) {
            patient.setGender(gender.equalsIgnoreCase("M") ? "M" : "F");
        }

        return patient;
    }

    // --- Getters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getDeathDate() { return deathDate; }
    public void setDeathDate(String deathDate) { this.deathDate = deathDate; }

    public String getSsn() { return ssn; }
    public void setSsn(String ssn) { this.ssn = ssn; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getZip() { return zip; }
    public void setZip(String zip) { this.zip = zip; }

    public String getRace() { return race; }
    public void setRace(String race) { this.race = race; }

    public String getEthnicity() { return ethnicity; }
    public void setEthnicity(String ethnicity) { this.ethnicity = ethnicity; }

    public String getMarital() { return marital; }
    public void setMarital(String marital) { this.marital = marital; }

    public String getCounty() { return county; }
    public void setCounty(String county) { this.county = county; }

    public String getIncome() { return income; }
    public void setIncome(String income) { this.income = income; }

    @Override
    public String toString() {
        return "SyntheaPatient{id='%s', name='%s %s', gender='%s', city='%s'}"
                .formatted(id, firstName, lastName, gender, city);
    }
}
