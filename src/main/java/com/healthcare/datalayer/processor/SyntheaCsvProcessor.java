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
 * Camel {@link Processor} that ingests Synthea-format CSV files and converts
 * {@link SyntheaPatient} records into the normalized {@link Patient} domain model.
 *
 * <p><b>Route:</b> Used in the {@code process-synthea-csv} sub-route defined in
 * {@link com.healthcare.datalayer.route.FtpPollingRoute}. Files are routed here when
 * their name starts with {@code "synthea-"} and ends with {@code ".csv"}. The route
 * unmarshals the CSV via Bindy into a {@code List<SyntheaPatient>} before delegating
 * to this processor.</p>
 *
 * <p>Synthea is an open-source synthetic patient generator. Its CSV output uses a
 * different column layout than the standard patient CSV format, hence the need for a
 * dedicated processor that maps via {@link SyntheaPatient#toDomainPatient()}.</p>
 *
 * <h3>Exchange contract</h3>
 * <ul>
 *   <li><b>Input body:</b> {@code List<SyntheaPatient>} — records unmarshalled from Synthea CSV</li>
 *   <li><b>Input header:</b> {@code CamelFileName} — the source file name on the FTP server</li>
 *   <li><b>Output body:</b> {@link ClinicalDocument} wrapping the converted patients</li>
 *   <li><b>Output headers:</b> {@code documentId}, {@code recordCount},
 *       {@code sourceFormat} ("SYNTHEA_CSV")</li>
 * </ul>
 *
 * @see com.healthcare.datalayer.route.FtpPollingRoute
 * @see SyntheaPatient#toDomainPatient()
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

    /**
     * Processes a Camel exchange containing a list of Synthea-parsed
     * {@link SyntheaPatient} records.
     *
     * <ol>
     *   <li>Reads the source file name from the {@code CamelFileName} header.</li>
     *   <li>Generates a unique document ID for traceability.</li>
     *   <li>Extracts the {@code List<SyntheaPatient>} from the exchange body.</li>
     *   <li>Creates a {@link ClinicalDocument} with format {@code "SYNTHEA_CSV"}.</li>
     *   <li>Converts each {@link SyntheaPatient} to a {@link Patient} via
     *       {@link SyntheaPatient#toDomainPatient()}, tags it with the source file,
     *       and stores it in the {@code patientStore}.</li>
     *   <li>Stores the completed document in the {@code documentStore}.</li>
     *   <li>Sets the exchange body to the document and populates output headers.</li>
     * </ol>
     *
     * @param exchange the Camel exchange; body must be a {@code List<SyntheaPatient>}
     * @throws Exception if patient conversion or storage fails
     */
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
