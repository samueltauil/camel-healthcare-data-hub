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

/**
 * Camel {@link Processor} that transforms a {@link ClinicalDocument} into an HL7 FHIR R4
 * {@link Bundle} of type {@code TRANSACTION}, suitable for posting to a FHIR server.
 *
 * <p><b>Route:</b> Used in the {@code fhir-publisher} route defined in
 * {@link com.healthcare.datalayer.route.FhirPublisherRoute}. The route invokes this
 * processor to convert ingested patients and observations into FHIR resources, then
 * posts the resulting bundle to the configured FHIR server.</p>
 *
 * <h3>Exchange contract</h3>
 * <ul>
 *   <li><b>Input body:</b> {@link ClinicalDocument} containing patients and observations</li>
 *   <li><b>Output body:</b> FHIR R4 {@link Bundle} (transaction bundle with POST entries)</li>
 * </ul>
 *
 * @see com.healthcare.datalayer.route.FhirPublisherRoute
 */
@ApplicationScoped
@Named("fhirBundleProcessor")
public class FhirBundleProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(FhirBundleProcessor.class);

    /**
     * Converts the {@link ClinicalDocument} in the exchange body into a FHIR R4
     * {@link Bundle}.
     *
     * <ol>
     *   <li>Creates a new {@link Bundle} with type {@code TRANSACTION} and a generated UUID.</li>
     *   <li>Iterates over each {@link Patient} in the document and converts it to a
     *       FHIR {@code Patient} resource via {@link #toFhirPatient}, adding it as a
     *       {@code POST} entry.</li>
     *   <li>Iterates over each {@link Observation} in the document and converts it to a
     *       FHIR {@code Observation} resource via {@link #toFhirObservation}, adding it
     *       as a {@code POST} entry.</li>
     *   <li>Replaces the exchange body with the completed bundle.</li>
     * </ol>
     *
     * @param exchange the Camel exchange; body must be a {@link ClinicalDocument}
     * @throws Exception if FHIR resource construction fails
     */
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

    /**
     * Converts a domain {@link Patient} into a FHIR R4
     * {@link org.hl7.fhir.r4.model.Patient} resource.
     *
     * <p>Maps the following fields:</p>
     * <ul>
     *   <li>Patient ID → FHIR identifier with system {@code urn:healthcare:patient-id}</li>
     *   <li>Name → FHIR HumanName (family + given)</li>
     *   <li>Date of birth → FHIR birthDate</li>
     *   <li>Gender → FHIR AdministrativeGender ({@code M}=MALE, {@code F}=FEMALE, else UNKNOWN)</li>
     *   <li>Phone / email → FHIR ContactPoint telecom entries</li>
     *   <li>Address → FHIR Address (line, city, state, postalCode)</li>
     * </ul>
     *
     * @param patient the domain patient to convert
     * @return a fully populated FHIR Patient resource
     */
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

    /**
     * Converts a domain {@link Observation} into a FHIR R4
     * {@link org.hl7.fhir.r4.model.Observation} resource.
     *
     * <p>Maps the following fields:</p>
     * <ul>
     *   <li>Observation ID → FHIR identifier with system {@code urn:healthcare:observation-id}</li>
     *   <li>Status → always {@code FINAL}</li>
     *   <li>Code / display → FHIR CodeableConcept (defaults system to LOINC if not set)</li>
     *   <li>Subject → FHIR Reference using a conditional reference by patient identifier</li>
     *   <li>Value → FHIR {@link Quantity} if parseable as a number, otherwise {@link StringType}</li>
     * </ul>
     *
     * @param obs the domain observation to convert
     * @return a fully populated FHIR Observation resource
     */
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
