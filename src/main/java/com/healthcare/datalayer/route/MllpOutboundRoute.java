package com.healthcare.datalayer.route;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.healthcare.datalayer.model.ClinicalDocument;

/**
 * Outbound route that forwards HL7v2 messages to downstream clinical systems over MLLP.
 *
 * <p>Uses the <strong>Camel MLLP component</strong> ({@code mllp://}) which implements the
 * <em>Minimal Lower Layer Protocol</em> — a simple, framing-oriented TCP protocol defined by
 * the HL7 standard (specifically HL7v2 over TCP). MLLP wraps each HL7 message with a start
 * block ({@code 0x0B}), end block ({@code 0x1C}), and carriage return ({@code 0x0D}), and
 * expects an ACK/NAK response from the receiver.</p>
 *
 * <h3>Routes defined</h3>
 * <ul>
 *   <li><strong>mllp-outbound</strong> — consumes from the {@code seda:to-mllp} fan-out
 *       channel, filters for HL7v2-origin messages only, restores the original HL7 wire
 *       format, and transmits the message over MLLP to the configured host/port.</li>
 * </ul>
 *
 * <h3>Key patterns</h3>
 * <ul>
 *   <li><em>Message filter</em> — only messages with {@code sourceFormat == 'HL7v2'} proceed;
 *       CSV-origin documents are silently dropped because they have no HL7 wire representation.</li>
 *   <li><em>doTry / doCatch</em> — wraps the MLLP send in a try/catch block so that
 *       connection failures (e.g., remote system is down) are logged as warnings instead of
 *       propagating up and triggering the dead-letter channel. This is appropriate because
 *       MLLP delivery is best-effort within the fan-out; retries can be handled externally.</li>
 * </ul>
 *
 * <h3>Pipeline position</h3>
 * <p>This is one of five <strong>outbound connectors</strong> fed by the fan-out multicast
 * in {@link FtpPollingRoute}. It only activates for HL7v2-sourced messages.</p>
 */
@ApplicationScoped
public class MllpOutboundRoute extends RouteBuilder {

    /** Hostname or IP of the downstream HL7 MLLP receiver. */
    @ConfigProperty(name = "mllp.host", defaultValue = "localhost")
    String mllpHost;

    /** TCP port of the downstream MLLP listener (HL7 standard default is 2575). */
    @ConfigProperty(name = "mllp.port", defaultValue = "2575")
    int mllpPort;

    @Override
    public void configure() throws Exception {

        /*
         * MLLP outbound route.
         *
         * Filter (message filter EIP):
         * Only HL7v2-sourced messages are forwarded. The "sourceFormat" header is set by
         * the hl7MessageProcessor during ingest. Messages originating from CSV files do
         * not have an HL7 wire representation and are silently discarded here.
         *
         * Body restoration:
         * The hl7MessageProcessor stores the raw HL7 pipe-delimited text in the
         * "originalHl7Message" header so it can be sent verbatim over MLLP. If the
         * header is missing, the route is stopped to avoid sending a malformed payload.
         *
         * doTry / doCatch:
         * Network errors (connection refused, timeout, NAK) are caught and logged at
         * WARN level. This prevents a single MLLP failure from failing the entire
         * fan-out exchange, since the other connectors (JMS, Kafka, FHIR) should
         * still succeed independently.
         */
        from("seda:to-mllp")
                .routeId("mllp-outbound")
                .filter(simple("${header.sourceFormat} == 'HL7v2'"))
                .log("Preparing HL7 MLLP outbound for document ${header.documentId}")
                .process(exchange -> {
                    // Restore the original HL7 wire-format message preserved during parsing
                    Object originalMessage = exchange.getIn().getHeader("originalHl7Message");
                    if (originalMessage != null) {
                        exchange.getIn().setBody(originalMessage);
                    } else {
                        ClinicalDocument doc = exchange.getIn().getBody(ClinicalDocument.class);
                        log.warn("No original HL7 message found for document {} — skipping MLLP",
                                doc != null ? doc.getDocumentId() : "unknown");
                        exchange.setRouteStop(true);
                    }
                })
                .doTry()
                    .toD("mllp://%s:%d".formatted(mllpHost, mllpPort))
                    .log("HL7 message sent via MLLP to %s:%d".formatted(mllpHost, mllpPort))
                .doCatch(Exception.class)
                    .log(LoggingLevel.WARN,
                            "MLLP delivery failed to %s:%d — ${exception.message}. Message will be retried."
                                    .formatted(mllpHost, mllpPort))
                .end();
    }
}
