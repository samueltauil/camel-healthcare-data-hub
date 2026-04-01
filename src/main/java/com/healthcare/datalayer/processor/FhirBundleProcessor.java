package com.healthcare.datalayer.processor;

import java.util.Date;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.healthcare.datalayer.model.ClinicalDocument;
import com.healthcare.datalayer.model.Observation;
import com.healthcare.datalayer.model.Patient;

@ApplicationScoped
@Named("fhirBundleProcessor")
public class FhirBundleProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(FhirBundleProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        ClinicalDocument document = exchange.getIn().getBody(ClinicalDocument.class);
        Bundle bundle = new Bundle();
        bundle.setId(UUID.randomUUID().toString());
        bundle.setType(Bundle.BundleType.TRANSACTION);
        bundle.setTimestamp(new Date());

        for (Patient patient : document.getPatients()) {
            org.hl7.fhir.r4.model.Patient fhirPatient = toFhirPatient(patient);
            bundle.addEntry()
                    .setFullUrl("urn:uuid:" + fhirPatient.getId())
                    .setResource(fhirPatient)
                    .getRequest()
                    .setMethod(Bundle.HTTPVerb.POST)
                    .setUrl("Patient");
        }

        for (Observation observation : document.getObservations()) {
            org.hl7.fhir.r4.model.Observation fhirObservation = toFhirObservation(observation);
            bundle.addEntry()
                    .setFullUrl("urn:uuid:" + fhirObservation.getId())
                    .setResource(fhirObservation)
                    .getRequest()
                    .setMethod(Bundle.HTTPVerb.POST)
                    .setUrl("Observation");
        }

        LOG.info("Built FHIR Bundle with {} entries from document {}",
                bundle.getEntry().size(), document.getDocumentId());

        exchange.getIn().setBody(bundle);
    }

    private org.hl7.fhir.r4.model.Patient toFhirPatient(Patient patient) {
        org.hl7.fhir.r4.model.Patient fhir = new org.hl7.fhir.r4.model.Patient();
        fhir.setId(UUID.randomUUID().toString());

        fhir.addIdentifier()
                .setSystem("urn:healthcare:patient-id")
                .setValue(patient.getPatientId());

        fhir.addName()
                .setFamily(patient.getLastName())
                .addGiven(patient.getFirstName());

        if (patient.getDateOfBirth() != null) {
            fhir.setBirthDate(java.sql.Date.valueOf(patient.getDateOfBirth()));
        }

        if (patient.getGender() != null) {
            fhir.setGender(switch (patient.getGender().toUpperCase()) {
                case "M" -> Enumerations.AdministrativeGender.MALE;
                case "F" -> Enumerations.AdministrativeGender.FEMALE;
                default -> Enumerations.AdministrativeGender.UNKNOWN;
            });
        }

        if (patient.getPhoneNumber() != null) {
            fhir.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(patient.getPhoneNumber());
        }

        if (patient.getEmail() != null) {
            fhir.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.EMAIL)
                    .setValue(patient.getEmail());
        }

        if (patient.getAddressLine1() != null) {
            fhir.addAddress()
                    .addLine(patient.getAddressLine1())
                    .setCity(patient.getCity())
                    .setState(patient.getState())
                    .setPostalCode(patient.getZipCode());
        }

        return fhir;
    }

    private org.hl7.fhir.r4.model.Observation toFhirObservation(Observation obs) {
        org.hl7.fhir.r4.model.Observation fhir = new org.hl7.fhir.r4.model.Observation();
        fhir.setId(UUID.randomUUID().toString());

        fhir.addIdentifier()
                .setSystem("urn:healthcare:observation-id")
                .setValue(obs.getObservationId());

        fhir.setStatus(org.hl7.fhir.r4.model.Observation.ObservationStatus.FINAL);

        fhir.getCode()
                .addCoding()
                .setSystem(obs.getCodeSystem() != null ? obs.getCodeSystem() : "http://loinc.org")
                .setCode(obs.getCode())
                .setDisplay(obs.getDisplayName());

        fhir.setSubject(new Reference("Patient?identifier=" + obs.getPatientId()));

        if (obs.getValue() != null) {
            try {
                fhir.setValue(new Quantity()
                        .setValue(Double.parseDouble(obs.getValue()))
                        .setUnit(obs.getUnit())
                        .setSystem("http://unitsofmeasure.org")
                        .setCode(obs.getUnit()));
            } catch (NumberFormatException e) {
                fhir.setValue(new StringType(obs.getValue()));
            }
        }

        return fhir;
    }
}
