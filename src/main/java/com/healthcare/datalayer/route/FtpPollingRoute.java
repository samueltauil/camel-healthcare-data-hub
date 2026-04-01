package com.healthcare.datalayer.route;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.BindyType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.healthcare.datalayer.model.Patient;
import com.healthcare.datalayer.model.SyntheaPatient;

@ApplicationScoped
public class FtpPollingRoute extends RouteBuilder {

    @ConfigProperty(name = "ftp.host", defaultValue = "localhost")
    String ftpHost;

    @ConfigProperty(name = "ftp.port", defaultValue = "21")
    int ftpPort;

    @ConfigProperty(name = "ftp.username", defaultValue = "healthcare")
    String ftpUsername;

    @ConfigProperty(name = "ftp.password", defaultValue = "healthcare123")
    String ftpPassword;

    @ConfigProperty(name = "ftp.directory", defaultValue = "inbox")
    String ftpDirectory;

    @ConfigProperty(name = "ftp.poll.delay", defaultValue = "5000")
    long pollDelay;

    @Override
    public void configure() throws Exception {

        // Global error handler with dead-letter channel
        errorHandler(deadLetterChannel("direct:dead-letter")
                .maximumRedeliveries(3)
                .redeliveryDelay(1000)
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .logExhausted(true));

        // Dead-letter route
        from("direct:dead-letter")
                .routeId("dead-letter")
                .log(LoggingLevel.ERROR, "Dead letter received for file: ${header.CamelFileName} — ${exception.message}")
                .to("log:dead-letter?level=ERROR&showAll=true");

        // Main FTP polling route
        from("ftp://%s:%d/%s?username=%s&password=%s&delay=%d&move=.done&moveFailed=.error&idempotent=true&noop=false&delete=false"
                .formatted(ftpHost, ftpPort, ftpDirectory, ftpUsername, ftpPassword, pollDelay))
                .routeId("ftp-polling")
                .log("Received file from FTP: ${header.CamelFileName}")
                .choice()
                    .when(simple("${header.CamelFileName} starts with 'synthea-' && ${header.CamelFileName} ends with '.csv'"))
                        .log("Routing Synthea CSV file: ${header.CamelFileName}")
                        .to("direct:process-synthea-csv")
                    .when(simple("${header.CamelFileName} ends with '.csv'"))
                        .log("Routing CSV file: ${header.CamelFileName}")
                        .to("direct:process-csv")
                    .when(simple("${header.CamelFileName} ends with '.hl7'"))
                        .log("Routing HL7 file: ${header.CamelFileName}")
                        .to("direct:process-hl7")
                    .otherwise()
                        .log(LoggingLevel.WARN, "Unknown file type: ${header.CamelFileName} — skipping")
                .end();

        // CSV processing sub-route
        from("direct:process-csv")
                .routeId("process-csv")
                .unmarshal().bindy(BindyType.Csv, Patient.class)
                .process("csvPatientProcessor")
                .log("CSV processing complete: ${header.recordCount} records from ${header.CamelFileName}")
                .to("direct:fanout");

        // HL7 processing sub-route
        from("direct:process-hl7")
                .routeId("process-hl7")
                .unmarshal().hl7()
                .process("hl7MessageProcessor")
                .log("HL7 processing complete: ${header.recordCount} records from ${header.CamelFileName}")
                .to("direct:fanout");

        // Synthea CSV processing sub-route
        from("direct:process-synthea-csv")
                .routeId("process-synthea-csv")
                .unmarshal().bindy(BindyType.Csv, SyntheaPatient.class)
                .process("syntheaCsvProcessor")
                .log("Synthea CSV processing complete: ${header.recordCount} records from ${header.CamelFileName}")
                .to("direct:fanout");

        // Fan-out to all output connectors
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
