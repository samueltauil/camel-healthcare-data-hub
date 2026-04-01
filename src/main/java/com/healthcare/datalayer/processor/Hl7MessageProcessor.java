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

@ApplicationScoped
@Named("hl7MessageProcessor")
public class Hl7MessageProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(Hl7MessageProcessor.class);
    private static final DateTimeFormatter HL7_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Inject
    @Named("patientStore")
    Map<String, Patient> patientStore;

    @Inject
    @Named("documentStore")
    Map<String, ClinicalDocument> documentStore;

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

    private Patient parseAdtA01(ADT_A01 adt) throws Exception {
        PID pid = adt.getPID();

        Patient patient = new Patient();
        patient.setPatientId(pid.getPatientIdentifierList(0).getIDNumber().getValue());
        patient.setLastName(pid.getPatientName(0).getFamilyName().getSurname().getValue());
        patient.setFirstName(pid.getPatientName(0).getGivenName().getValue());

        String dobStr = pid.getDateTimeOfBirth().getTime().getValue();
        if (dobStr != null && dobStr.length() >= 8) {
            patient.setDateOfBirth(LocalDate.parse(dobStr.substring(0, 8), HL7_DATE));
        }

        patient.setGender(pid.getAdministrativeSex().getValue());

        if (pid.getPatientAddress(0) != null) {
            patient.setAddressLine1(pid.getPatientAddress(0).getStreetAddress().getStreetOrMailingAddress().getValue());
            patient.setCity(pid.getPatientAddress(0).getCity().getValue());
            patient.setState(pid.getPatientAddress(0).getStateOrProvince().getValue());
            patient.setZipCode(pid.getPatientAddress(0).getZipOrPostalCode().getValue());
        }

        if (pid.getPhoneNumberHome(0) != null) {
            patient.setPhoneNumber(pid.getPhoneNumberHome(0).getTelephoneNumber().getValue());
        }

        patient.setInsuranceId(pid.getSSNNumberPatient().getValue());

        return patient;
    }
}
