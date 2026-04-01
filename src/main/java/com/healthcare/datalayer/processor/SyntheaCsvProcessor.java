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
import com.healthcare.datalayer.model.SyntheaPatient;

/**
 * Processes Synthea-format CSV files (patients.csv from Synthea output).
 * Detects Synthea format by filename convention ("synthea-" prefix)
 * and converts SyntheaPatient records to the normalized Patient model.
 */
@ApplicationScoped
@Named("syntheaCsvProcessor")
public class SyntheaCsvProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(SyntheaCsvProcessor.class);

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
        List<SyntheaPatient> syntheaPatients = exchange.getIn().getBody(List.class);

        ClinicalDocument document = new ClinicalDocument(documentId, fileName, "SYNTHEA_CSV");

        for (SyntheaPatient sp : syntheaPatients) {
            Patient patient = sp.toDomainPatient();
            patient.setSourceFile(fileName);
            patientStore.put(patient.getPatientId(), patient);
            document.addPatient(patient);
            LOG.debug("Stored Synthea patient: {}", patient);
        }

        documentStore.put(documentId, document);

        LOG.info("Processed Synthea CSV file '{}': {} patients ingested (docId={})",
                fileName, syntheaPatients.size(), documentId);

        exchange.getIn().setBody(document);
        exchange.getIn().setHeader("documentId", documentId);
        exchange.getIn().setHeader("recordCount", syntheaPatients.size());
        exchange.getIn().setHeader("sourceFormat", "SYNTHEA_CSV");
    }
}
