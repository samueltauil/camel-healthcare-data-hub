package com.healthcare.datalayer.route;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.BindyType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.healthcare.datalayer.model.Patient;
import com.healthcare.datalayer.model.SyntheaPatient;

/**
 * Main inbound route that polls an FTP server for healthcare data files.
 *
 * <p>Uses the <strong>Camel FTP component</strong> ({@code ftp://}) to periodically connect to a
 * remote FTP server and consume new files from a configured inbox directory. The FTP component
 * wraps Apache Commons Net and handles connection pooling, file listing, and post-processing
 * moves transparently.</p>
 *
 * <h3>Routes defined</h3>
 * <ul>
 *   <li><strong>ftp-polling</strong> — polls the FTP inbox and dispatches files via a
 *       <em>content-based router</em> ({@code choice()}) that inspects the filename to decide
 *       which processing sub-route to invoke.</li>
 *   <li><strong>process-csv</strong> — unmarshals generic patient CSV files using Bindy.</li>
 *   <li><strong>process-synthea-csv</strong> — unmarshals Synthea-generated CSV files
 *       (prefixed {@code synthea-}) into the {@link SyntheaPatient} model.</li>
 *   <li><strong>process-hl7</strong> — unmarshals HL7v2 pipe-delimited messages.</li>
 *   <li><strong>fanout</strong> — broadcasts a processed {@code ClinicalDocument} to all
 *       downstream output connectors in parallel via the <em>multicast</em> EIP.</li>
 *   <li><strong>dead-letter</strong> — catches messages that exhaust all retry attempts.</li>
 * </ul>
 *
 * <h3>Key patterns</h3>
 * <ul>
 *   <li><em>Dead-letter channel</em> — the global {@code errorHandler} retries up to 3 times
 *       with a 1-second delay. After exhaustion, the message is routed to
 *       {@code direct:dead-letter} for error logging.</li>
 *   <li><em>Idempotent consumer</em> — {@code idempotent=true} on the FTP URI ensures each
 *       file is consumed only once, even across restarts (backed by Camel's default in-memory
 *       idempotent repository).</li>
 *   <li><em>Content-based router</em> — {@code choice()/when()/otherwise()} inspects the
 *       {@code CamelFileName} header to dispatch to the correct format-specific sub-route.</li>
 *   <li><em>Fan-out (multicast)</em> — after processing, the document is sent to five
 *       SEDA queues concurrently ({@code parallelProcessing()}), decoupling inbound
 *       ingestion from outbound publishing.</li>
 * </ul>
 *
 * <h3>Pipeline position</h3>
 * <p>This is the <strong>entry point</strong> of the data-layer pipeline. Files land on FTP,
 * are parsed into a {@code ClinicalDocument}, and then fanned out to REST, JMS, Kafka, FHIR,
 * and MLLP connectors.</p>
 */
@ApplicationScoped
public class FtpPollingRoute extends RouteBuilder {

    /** FTP server hostname or IP address. */
    @ConfigProperty(name = "ftp.host", defaultValue = "localhost")
    String ftpHost;

    /** FTP server port (standard FTP is 21). */
    @ConfigProperty(name = "ftp.port", defaultValue = "21")
    int ftpPort;

    /** FTP login username. */
    @ConfigProperty(name = "ftp.username", defaultValue = "healthcare")
    String ftpUsername;

    /** FTP login password. */
    @ConfigProperty(name = "ftp.password", defaultValue = "healthcare123")
    String ftpPassword;

    /** Remote directory on the FTP server to poll for new files. */
    @ConfigProperty(name = "ftp.directory", defaultValue = "inbox")
    String ftpDirectory;

    /** Interval in milliseconds between successive FTP poll cycles. */
    @ConfigProperty(name = "ftp.poll.delay", defaultValue = "5000")
    long pollDelay;

    @Override
    public void configure() throws Exception {

        /*
         * Dead-letter channel (error handler):
         * Failed exchanges are retried up to 3 times with a 1-second gap.
         * If all retries are exhausted the exchange is forwarded to the
         * "direct:dead-letter" route so the failure can be logged and audited.
         */
        errorHandler(deadLetterChannel("direct:dead-letter")
                .maximumRedeliveries(3)
                .redeliveryDelay(1000)
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .logExhausted(true));

        // Dead-letter route — terminal sink for messages that failed all retry attempts
        from("direct:dead-letter")
                .routeId("dead-letter")
                .log(LoggingLevel.ERROR, "Dead letter received for file: ${header.CamelFileName} — ${exception.message}")
                .to("log:dead-letter?level=ERROR&showAll=true");

        /*
         * Main FTP polling route.
         * URI options:
         *   move=.done       — successfully processed files are moved to .done/
         *   moveFailed=.error — files that cause an unrecoverable error go to .error/
         *   idempotent=true   — prevents re-processing the same file (idempotent consumer EIP)
         *   noop=false        — files are moved after processing (not left in place)
         *   delete=false      — files are moved, not deleted
         *
         * Content-based router (choice/when/otherwise):
         * Inspects the CamelFileName header to select the correct parser:
         *   1. synthea-*.csv  → Synthea-format CSV processor
         *   2. *.csv          → generic patient CSV processor
         *   3. *.hl7          → HL7v2 pipe-delimited message processor
         *   4. anything else  → logged as a warning and skipped
         */
        from("ftp://%s:%d/%s?username=%s&password=%s&delay=%d&passiveMode=true&move=.done&moveFailed=.error&idempotent=true&noop=false&delete=false"
                .formatted(ftpHost, ftpPort, ftpDirectory, ftpUsername, ftpPassword, pollDelay))
                .routeId("ftp-polling")
                .log("Received file from FTP: ${header.CamelFileName}")
                .choice()
                    .when(simple("${header.CamelFileName} == 'synthea-patients.csv'"))
                        .log("Routing Synthea patients CSV: ${header.CamelFileName}")
                        .to("direct:process-synthea-csv")
                    .when(simple("${header.CamelFileName} starts with 'synthea-' && ${header.CamelFileName} ends with '.csv'"))
                        .log(LoggingLevel.INFO, "Skipping non-patient Synthea CSV: ${header.CamelFileName} (no parser configured)")
                    .when(simple("${header.CamelFileName} ends with '.csv'"))
                        .log("Routing CSV file: ${header.CamelFileName}")
                        .to("direct:process-csv")
                    .when(simple("${header.CamelFileName} ends with '.hl7'"))
                        .log("Routing HL7 file: ${header.CamelFileName}")
                        .to("direct:process-hl7")
                    .otherwise()
                        .log(LoggingLevel.WARN, "Unknown file type: ${header.CamelFileName} — skipping")
                .end();

        // CSV processing sub-route: unmarshals CSV rows into Patient POJOs via Bindy,
        // then delegates to csvPatientProcessor to build a ClinicalDocument.
        from("direct:process-csv")
                .routeId("process-csv")
                .unmarshal().bindy(BindyType.Csv, Patient.class)
                .process("csvPatientProcessor")
                .log("CSV processing complete: ${header.recordCount} records from ${header.CamelFileName}")
                .to("direct:fanout");

        // HL7 processing sub-route: unmarshals the pipe-delimited HL7v2 message using
        // the camel-hl7 data format, then delegates to hl7MessageProcessor.
        from("direct:process-hl7")
                .routeId("process-hl7")
                .unmarshal().hl7()
                .process("hl7MessageProcessor")
                .log("HL7 processing complete: ${header.recordCount} records from ${header.CamelFileName}")
                .to("direct:fanout");

        // Synthea CSV processing sub-route: handles CSV exports from the Synthea™
        // patient generator which use a different column layout than standard CSVs.
        from("direct:process-synthea-csv")
                .routeId("process-synthea-csv")
                .unmarshal().bindy(BindyType.Csv, SyntheaPatient.class)
                .process("syntheaCsvProcessor")
                .log("Synthea CSV processing complete: ${header.recordCount} records from ${header.CamelFileName}")
                .to("direct:fanout");

        /*
         * Fan-out route (multicast EIP with parallel processing).
         * Sends a copy of the ClinicalDocument to every downstream connector
         * concurrently via SEDA queues, which act as in-memory async buffers.
         * This decouples inbound file ingestion speed from the latency of each
         * individual outbound system (REST store, JMS, Kafka, FHIR, MLLP).
         */
        from("direct:fanout")
                .routeId("fanout")
                .log("Fanning out document ${header.documentId} to all connectors")
                .multicast().parallelProcessing()
                    .to("seda:to-rest-store",
                        "seda:to-jms",
                        "seda:to-kafka",
                        "seda:to-fhir",
                        "seda:to-mllp")
                .end();
    }
}
