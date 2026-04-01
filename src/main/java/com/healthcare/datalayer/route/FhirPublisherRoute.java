package com.healthcare.datalayer.route;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class FhirPublisherRoute extends RouteBuilder {

    @ConfigProperty(name = "fhir.server.url", defaultValue = "http://localhost:8090/fhir")
    String fhirServerUrl;

    @Override
    public void configure() throws Exception {

        from("seda:to-fhir")
                .routeId("fhir-publisher")
                .log("Transforming document ${header.documentId} to FHIR R4 Bundle")
                .process("fhirBundleProcessor")
                .doTry()
                    .toD("fhir://transaction/withBundle?serverUrl=%s&fhirVersion=R4".formatted(fhirServerUrl))
                    .log("FHIR Bundle posted to %s".formatted(fhirServerUrl))
                .doCatch(Exception.class)
                    .log(LoggingLevel.WARN,
                            "FHIR publish failed to %s — ${exception.message}".formatted(fhirServerUrl))
                .end();
    }
}
