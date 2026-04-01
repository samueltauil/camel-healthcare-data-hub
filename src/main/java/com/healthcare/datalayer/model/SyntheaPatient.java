package com.healthcare.datalayer.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Maps the Synthea-generated patients.csv format.
 * Synthea CSV columns: Id, BIRTHDATE, DEATHDATE, SSN, DRIVERS, PASSPORT,
 * PREFIX, FIRST, LAST, SUFFIX, MAIDEN, MARITAL, RACE, ETHNICITY, GENDER,
 * BIRTHPLACE, ADDRESS, CITY, STATE, COUNTY, FIPS, ZIP, LAT, LON,
 * HEALTHCARE_EXPENSES, HEALTHCARE_COVERAGE, INCOME
 */
@CsvRecord(separator = ",", skipFirstLine = true)
public class SyntheaPatient {

    @DataField(pos = 1)
    private String id;

    @DataField(pos = 2, pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    @DataField(pos = 3)
    private String deathDate;

    @DataField(pos = 4)
    private String ssn;

    @DataField(pos = 5)
    private String drivers;

    @DataField(pos = 6)
    private String passport;

    @DataField(pos = 7)
    private String prefix;

    @DataField(pos = 8)
    private String firstName;

    @DataField(pos = 9)
    private String lastName;

    @DataField(pos = 10)
    private String suffix;

    @DataField(pos = 11)
    private String maiden;

    @DataField(pos = 12)
    private String marital;

    @DataField(pos = 13)
    private String race;

    @DataField(pos = 14)
    private String ethnicity;

    @DataField(pos = 15)
    private String gender;

    @DataField(pos = 16)
    private String birthplace;

    @DataField(pos = 17)
    private String address;

    @DataField(pos = 18)
    private String city;

    @DataField(pos = 19)
    private String state;

    @DataField(pos = 20)
    private String county;

    @DataField(pos = 21)
    private String fips;

    @DataField(pos = 22)
    private String zip;

    @DataField(pos = 23)
    private String lat;

    @DataField(pos = 24)
    private String lon;

    @DataField(pos = 25)
    private String healthcareExpenses;

    @DataField(pos = 26)
    private String healthcareCoverage;

    @DataField(pos = 27)
    private String income;

    public SyntheaPatient() {}

    /**
     * Converts this Synthea-format patient to the normalized domain model.
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
