package com.healthcare.datalayer.route;

import java.util.ArrayList;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;

import com.healthcare.datalayer.model.ClinicalDocument;
import com.healthcare.datalayer.model.Observation;
import com.healthcare.datalayer.model.Patient;

@ApplicationScoped
public class RestApiRoute extends RouteBuilder {

    @Inject
    @Named("patientStore")
    Map<String, Patient> patientStore;

    @Inject
    @Named("observationStore")
    Map<String, Observation> observationStore;

    @Inject
    @Named("documentStore")
    Map<String, ClinicalDocument> documentStore;

    @Override
    public void configure() throws Exception {

        restConfiguration()
                .component("platform-http")
                .bindingMode(RestBindingMode.json)
                .dataFormatProperty("prettyPrint", "true")
                .contextPath("/api")
                .apiContextPath("/openapi")
                .apiProperty("api.title", "Healthcare Data Layer API")
                .apiProperty("api.version", "1.0.0")
                .apiProperty("api.description", "REST API for healthcare flat-file data ingestion");

        // --- Patient endpoints ---
        rest("/patients")
                .get()
                    .description("List all ingested patients")
                    .produces("application/json")
                    .to("direct:get-all-patients")
                .get("/{patientId}")
                    .description("Get a patient by ID")
                    .produces("application/json")
                    .to("direct:get-patient");

        // --- Observation endpoints ---
        rest("/observations")
                .get()
                    .description("List all observations")
                    .produces("application/json")
                    .to("direct:get-all-observations")
                .get("/patient/{patientId}")
                    .description("Get observations for a patient")
                    .produces("application/json")
                    .to("direct:get-observations-by-patient");

        // --- Document endpoints ---
        rest("/documents")
                .get()
                    .description("List all ingested documents")
                    .produces("application/json")
                    .to("direct:get-all-documents")
                .get("/{documentId}")
                    .description("Get a document by ID")
                    .produces("application/json")
                    .to("direct:get-document");

        // --- Health check ---
        rest("/health")
                .get()
                    .description("Health check")
                    .produces("application/json")
                    .to("direct:health");

        // --- Route implementations ---

        from("direct:get-all-patients")
                .routeId("rest-get-all-patients")
                .process(exchange -> exchange.getIn().setBody(new ArrayList<>(patientStore.values())));

        from("direct:get-patient")
                .routeId("rest-get-patient")
                .process(exchange -> {
                    String id = exchange.getIn().getHeader("patientId", String.class);
                    Patient patient = patientStore.get(id);
                    if (patient == null) {
                        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                        exchange.getIn().setBody(Map.of("error", "Patient not found", "patientId", id));
                    } else {
                        exchange.getIn().setBody(patient);
                    }
                });

        from("direct:get-all-observations")
                .routeId("rest-get-all-observations")
                .process(exchange -> exchange.getIn().setBody(new ArrayList<>(observationStore.values())));

        from("direct:get-observations-by-patient")
                .routeId("rest-get-observations-by-patient")
                .process(exchange -> {
                    String patientId = exchange.getIn().getHeader("patientId", String.class);
                    var observations = observationStore.values().stream()
                            .filter(o -> patientId.equals(o.getPatientId()))
                            .toList();
                    exchange.getIn().setBody(observations);
                });

        from("direct:get-all-documents")
                .routeId("rest-get-all-documents")
                .process(exchange -> exchange.getIn().setBody(new ArrayList<>(documentStore.values())));

        from("direct:get-document")
                .routeId("rest-get-document")
                .process(exchange -> {
                    String id = exchange.getIn().getHeader("documentId", String.class);
                    ClinicalDocument doc = documentStore.get(id);
                    if (doc == null) {
                        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                        exchange.getIn().setBody(Map.of("error", "Document not found", "documentId", id));
                    } else {
                        exchange.getIn().setBody(doc);
                    }
                });

        from("direct:health")
                .routeId("rest-health")
                .process(exchange -> exchange.getIn().setBody(Map.of(
                        "status", "UP",
                        "patients", patientStore.size(),
                        "observations", observationStore.size(),
                        "documents", documentStore.size()
                )));

        // SEDA consumer: store data for REST queries (no-op since processors already store)
        from("seda:to-rest-store")
                .routeId("to-rest-store")
                .log("Data available via REST API for document ${header.documentId}");
    }
}
