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

/**
 * Camel {@link Processor} that ingests a pre-parsed list of {@link Patient} records
 * from a standard CSV file and persists them into the in-memory patient and document stores.
 *
 * <p><b>Route:</b> Used in the {@code process-csv} sub-route defined in
 * {@link com.healthcare.datalayer.route.FtpPollingRoute}. The route unmarshals the CSV
 * via Bindy into a {@code List<Patient>} before delegating to this processor.</p>
 *
 * <h3>Exchange contract</h3>
 * <ul>
 *   <li><b>Input body:</b> {@code List<Patient>} — patients unmarshalled from CSV by Bindy</li>
 *   <li><b>Input header:</b> {@code CamelFileName} — the source file name on the FTP server</li>
 *   <li><b>Output body:</b> {@link ClinicalDocument} wrapping the ingested patients</li>
 *   <li><b>Output headers:</b> {@code documentId}, {@code recordCount}, {@code sourceFormat} ("CSV")</li>
 * </ul>
 *
 * @see com.healthcare.datalayer.route.FtpPollingRoute
 */
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

    /**
     * Processes a Camel exchange containing a list of CSV-parsed {@link Patient} records.
     *
     * <ol>
     *   <li>Reads the source file name from the {@code CamelFileName} header.</li>
     *   <li>Generates a unique document ID for traceability.</li>
     *   <li>Extracts the {@code List<Patient>} from the exchange body.</li>
     *   <li>Creates a {@link ClinicalDocument} to group the patients under.</li>
     *   <li>Iterates over each patient, tags it with the source file, and stores it
     *       in the {@code patientStore} keyed by patient ID.</li>
     *   <li>Stores the completed document in the {@code documentStore}.</li>
     *   <li>Sets the exchange body to the document and populates output headers.</li>
     * </ol>
     *
     * @param exchange the Camel exchange; body must be a {@code List<Patient>}
     * @throws Exception if patient extraction or storage fails
     */
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
