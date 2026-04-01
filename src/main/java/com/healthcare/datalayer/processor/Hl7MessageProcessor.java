package com.healthcare.datalayer.processor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.model.v25.segment.MSH;

import com.healthcare.datalayer.model.ClinicalDocument;
import com.healthcare.datalayer.model.Patient;

/**
 * Camel {@link Processor} that parses an HL7 v2.5 message and extracts patient
 * demographics into the in-memory patient and document stores.
 *
 * <p><b>Route:</b> Used in the {@code process-hl7} sub-route defined in
 * {@link com.healthcare.datalayer.route.FtpPollingRoute}. The route unmarshals the raw
 * HL7 payload via the Camel HL7 data format before delegating to this processor.</p>
 *
 * <p>Currently only {@code ADT^A01} (patient admission) messages are fully parsed.
 * All other message types are logged with their MSH message-type and trigger-event
 * codes and stored as raw documents for downstream processing.</p>
 *
 * <h3>Exchange contract</h3>
 * <ul>
 *   <li><b>Input body:</b> {@link Message} — HAPI HL7 message object</li>
 *   <li><b>Input header:</b> {@code CamelFileName} — source file name (defaults to
 *       {@code "hl7-message"} when absent)</li>
 *   <li><b>Output body:</b> {@link ClinicalDocument} wrapping any extracted patients</li>
 *   <li><b>Output headers:</b> {@code documentId}, {@code recordCount},
 *       {@code sourceFormat} ("HL7v2"), {@code originalHl7Message}</li>
 * </ul>
 *
 * @see com.healthcare.datalayer.route.FtpPollingRoute
 */
@ApplicationScoped
@Named("hl7MessageProcessor")
public class Hl7MessageProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(Hl7MessageProcessor.class);

    /** Date formatter for HL7 date fields which use the compact {@code yyyyMMdd} format. */
    private static final DateTimeFormatter HL7_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Inject
    @Named("patientStore")
    Map<String, Patient> patientStore;

    @Inject
    @Named("documentStore")
    Map<String, ClinicalDocument> documentStore;

    /**
     * Processes a Camel exchange containing a parsed HL7 v2.5 {@link Message}.
     *
     * <ol>
     *   <li>Extracts the HAPI {@link Message} from the exchange body.</li>
     *   <li>Reads the source file name from headers (defaulting to {@code "hl7-message"}).</li>
     *   <li>Creates a new {@link ClinicalDocument} with format {@code "HL7v2"}.</li>
     *   <li>If the message is an {@code ADT_A01}, delegates to {@link #parseAdtA01} to
     *       extract patient demographics and stores them.</li>
     *   <li>For unsupported message types, logs the MSH message type and trigger event
     *       for diagnostic purposes.</li>
     *   <li>Sets output body, headers, and preserves the original HL7 message for
     *       potential MLLP forwarding downstream.</li>
     * </ol>
     *
     * @param exchange the Camel exchange; body must be a HAPI {@link Message}
     * @throws Exception if HL7 parsing or patient extraction fails
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getIn().getBody(Message.class);
        String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, "hl7-message", String.class);
        String documentId = UUID.randomUUID().toString();

        ClinicalDocument document = new ClinicalDocument(documentId, fileName, "HL7v2");

        if (message instanceof ADT_A01 adt) {
            Patient patient = parseAdtA01(adt);
            patient.setSourceFile(fileName);
            patientStore.put(patient.getPatientId(), patient);
            document.addPatient(patient);
            LOG.info("Parsed HL7 ADT^A01 patient: {}", patient);
        } else {
            LOG.warn("Unsupported HL7 message type: {}", message.getClass().getSimpleName());
            MSH msh = (MSH) message.get("MSH");
            String msgType = msh.getMessageType().getMessageCode().getValue();
            String triggerEvent = msh.getMessageType().getTriggerEvent().getValue();
            LOG.info("Received HL7 {}^{} — storing raw for downstream processing", msgType, triggerEvent);
        }

        documentStore.put(documentId, document);

        exchange.getIn().setBody(document);
        exchange.getIn().setHeader("documentId", documentId);
        exchange.getIn().setHeader("recordCount", document.getPatients().size());
        exchange.getIn().setHeader("sourceFormat", "HL7v2");
        // Preserve original HL7 message for MLLP forwarding
        exchange.getIn().setHeader("originalHl7Message", message);
    }

    /**
     * Extracts patient demographics from an HL7 v2.5 ADT^A01 (admission) message.
     *
     * <p>Maps fields from the PID (Patient Identification) segment:</p>
     * <ul>
     *   <li>PID-3  → patient ID (first repetition)</li>
     *   <li>PID-5  → patient name (family / given)</li>
     *   <li>PID-7  → date of birth (truncated to 8-char {@code yyyyMMdd})</li>
     *   <li>PID-8  → administrative sex</li>
     *   <li>PID-11 → address (street, city, state, zip)</li>
     *   <li>PID-13 → home phone number</li>
     *   <li>PID-19 → SSN, mapped to {@code insuranceId}</li>
     * </ul>
     *
     * @param adt the parsed ADT_A01 message
     * @return a populated {@link Patient} domain object
     * @throws Exception if any PID field accessor fails
     */
    private Patient parseAdtA01(ADT_A01 adt) throws Exception {
        PID pid = adt.getPID();

        Patient patient = new Patient();
        // PID-3: Patient Identifier List — use the first repetition's ID number
        patient.setPatientId(pid.getPatientIdentifierList(0).getIDNumber().getValue());
        // PID-5: Patient Name — family surname and given name from first repetition
        patient.setLastName(pid.getPatientName(0).getFamilyName().getSurname().getValue());
        patient.setFirstName(pid.getPatientName(0).getGivenName().getValue());

        // PID-7: Date/Time of Birth — HL7 may include time; truncate to first 8 chars for date only
        String dobStr = pid.getDateTimeOfBirth().getTime().getValue();
        if (dobStr != null && dobStr.length() >= 8) {
            patient.setDateOfBirth(LocalDate.parse(dobStr.substring(0, 8), HL7_DATE));
        }

        patient.setGender(pid.getAdministrativeSex().getValue());

        // PID-11: Patient Address — first repetition
        if (pid.getPatientAddress(0) != null) {
            patient.setAddressLine1(pid.getPatientAddress(0).getStreetAddress().getStreetOrMailingAddress().getValue());
            patient.setCity(pid.getPatientAddress(0).getCity().getValue());
            patient.setState(pid.getPatientAddress(0).getStateOrProvince().getValue());
            patient.setZipCode(pid.getPatientAddress(0).getZipOrPostalCode().getValue());
        }

        // PID-13: Phone Number — Home
        if (pid.getPhoneNumberHome(0) != null) {
            patient.setPhoneNumber(pid.getPhoneNumberHome(0).getTelephoneNumber().getValue());
        }

        // PID-19: SSN — mapped to insuranceId in the domain model
        patient.setInsuranceId(pid.getSSNNumberPatient().getValue());

        return patient;
    }
}
