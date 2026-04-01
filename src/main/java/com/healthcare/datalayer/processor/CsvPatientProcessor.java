package com.healthcare.datalayer.processor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.healthcare.datalayer.model.ClinicalDocument;
import com.healthcare.datalayer.model.Patient;

@ApplicationScoped
@Named("csvPatientProcessor")
public class CsvPatientProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(CsvPatientProcessor.class);

    @Inject
    @Named("patientStore")
    Map<String, Patient> patientStore;

    @Inject
    @Named("documentStore")
    Map<String, ClinicalDocument> documentStore;

    @Override
    public void process(Exchange exchange) throws Exception {
        String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
        String documentId = UUID.randomUUID().toString();

        @SuppressWarnings("unchecked")
        List<Patient> patients = exchange.getIn().getBody(List.class);

        ClinicalDocument document = new ClinicalDocument(documentId, fileName, "CSV");

        for (Patient patient : patients) {
            patient.setSourceFile(fileName);
            patientStore.put(patient.getPatientId(), patient);
            document.addPatient(patient);
            LOG.debug("Stored patient: {}", patient);
        }

        documentStore.put(documentId, document);

        LOG.info("Processed CSV file '{}': {} patients ingested (docId={})",
                fileName, patients.size(), documentId);

        exchange.getIn().setBody(document);
        exchange.getIn().setHeader("documentId", documentId);
        exchange.getIn().setHeader("recordCount", patients.size());
        exchange.getIn().setHeader("sourceFormat", "CSV");
    }
}
