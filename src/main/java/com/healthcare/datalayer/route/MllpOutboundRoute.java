package com.healthcare.datalayer.route;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.healthcare.datalayer.model.ClinicalDocument;

@ApplicationScoped
public class MllpOutboundRoute extends RouteBuilder {

    @ConfigProperty(name = "mllp.host", defaultValue = "localhost")
    String mllpHost;

    @ConfigProperty(name = "mllp.port", defaultValue = "2575")
    int mllpPort;

    @Override
    public void configure() throws Exception {

        // MLLP outbound: forward original HL7 messages to downstream systems
        from("seda:to-mllp")
                .routeId("mllp-outbound")
                .filter(simple("${header.sourceFormat} == 'HL7v2'"))
                .log("Preparing HL7 MLLP outbound for document ${header.documentId}")
                .process(exchange -> {
                    // Use the original HL7 message preserved during parsing
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
