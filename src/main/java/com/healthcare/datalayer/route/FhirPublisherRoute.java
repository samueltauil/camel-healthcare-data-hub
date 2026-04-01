package com.healthcare.datalayer.route;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Outbound route that publishes clinical data to a FHIR R4 server as transaction bundles.
 *
 * <p>Uses the <strong>Camel FHIR component</strong> ({@code fhir://}) which wraps the
 * HAPI FHIR client library. The component URI follows the pattern
 * {@code fhir://<apiName>/<methodName>?options}. This route uses the
 * {@code transaction/withBundle} API, which POSTs a FHIR
 * <a href="https://www.hl7.org/fhir/bundle.html">Bundle</a> of type {@code transaction}
 * to the server's base URL. A transaction bundle allows multiple resources (Patient,
 * Observation, etc.) to be created or updated atomically in a single HTTP request.</p>
 *
 * <h3>Routes defined</h3>
 * <ul>
 *   <li><strong>fhir-publisher</strong> — consumes from {@code seda:to-fhir}, transforms
 *       the {@code ClinicalDocument} into a FHIR R4 Bundle via
 *       {@code fhirBundleProcessor}, and posts it to the configured FHIR server.</li>
 * </ul>
 *
 * <h3>Key patterns</h3>
 * <ul>
 *   <li><em>doTry / doCatch</em> — wraps the FHIR server call so that connectivity or
 *       validation errors are logged as warnings without failing the entire fan-out
 *       exchange.</li>
 * </ul>
 *
 * <h3>Pipeline position</h3>
 * <p>This is one of five <strong>outbound connectors</strong> in the fan-out from
 * {@link FtpPollingRoute}. It converts the internal {@code ClinicalDocument} model into
 * the HL7 FHIR standard and persists it to an external FHIR repository.</p>
 */
@ApplicationScoped
public class FhirPublisherRoute extends RouteBuilder {

    /** Base URL of the target FHIR R4 server (e.g., HAPI FHIR, Azure FHIR). */
    @ConfigProperty(name = "fhir.server.url", defaultValue = "http://localhost:8090/fhir")
    String fhirServerUrl;

    @Override
    public void configure() throws Exception {

        /*
         * FHIR publisher route.
         *
         * 1. fhirBundleProcessor converts the ClinicalDocument into a FHIR R4 Bundle
         *    of type "transaction", containing Patient, Observation, and other resources.
         * 2. The fhir:// component URI "transaction/withBundle" POSTs the bundle to the
         *    FHIR server atomically. The server processes all entries in a single
         *    database transaction and returns a Bundle of type "transaction-response".
         * 3. doTry/doCatch prevents FHIR server errors (HTTP 4xx/5xx, network timeouts)
         *    from propagating to the fan-out multicast and affecting other connectors.
         */
        from("seda:to-fhir")
                .routeId("fhir-publisher")
                .log("Transforming document ${header.documentId} to FHIR R4 Bundle")
                .process("fhirBundleProcessor")
                .doTry()
                    .toD("fhir://transaction/withBundle?serverUrl=%s&fhirVersion=R4&inBody=bundle".formatted(fhirServerUrl))
                    .log("FHIR Bundle posted to %s".formatted(fhirServerUrl))
                .doCatch(Exception.class)
                    .log(LoggingLevel.WARN,
                            "FHIR publish failed to %s — ${exception.message}".formatted(fhirServerUrl))
                .end();
    }
}
