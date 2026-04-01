package com.healthcare.datalayer.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.model.rest.RestBindingMode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
                .bindingMode(RestBindingMode.off)
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

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        JacksonDataFormat jsonFormat = new JacksonDataFormat();
        jsonFormat.setObjectMapper(mapper);

        from("direct:get-all-patients")
                .routeId("rest-get-all-patients")
                .process(exchange -> exchange.getIn().setBody(new ArrayList<>(patientStore.values())))
                .marshal(jsonFormat);

        from("direct:get-patient")
                .routeId("rest-get-patient")
                .process(exchange -> {
                    String id = exchange.getIn().getHeader("patientId", String.class);
                    Patient patient = patientStore.get(id);
                    if (patient == null) {
                        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                        HashMap<String, String> error = new HashMap<>();
                        error.put("error", "Patient not found");
                        error.put("patientId", id);
                        exchange.getIn().setBody(error);
                    } else {
                        exchange.getIn().setBody(patient);
                    }
                })
                .marshal(jsonFormat);

        from("direct:get-all-observations")
                .routeId("rest-get-all-observations")
                .process(exchange -> exchange.getIn().setBody(new ArrayList<>(observationStore.values())))
                .marshal(jsonFormat);

        from("direct:get-observations-by-patient")
                .routeId("rest-get-observations-by-patient")
                .process(exchange -> {
                    String patientId = exchange.getIn().getHeader("patientId", String.class);
                    var observations = observationStore.values().stream()
                            .filter(o -> patientId.equals(o.getPatientId()))
                            .toList();
                    exchange.getIn().setBody(new ArrayList<>(observations));
                })
                .marshal(jsonFormat);

        from("direct:get-all-documents")
                .routeId("rest-get-all-documents")
                .process(exchange -> exchange.getIn().setBody(new ArrayList<>(documentStore.values())))
                .marshal(jsonFormat);

        from("direct:get-document")
                .routeId("rest-get-document")
                .process(exchange -> {
                    String id = exchange.getIn().getHeader("documentId", String.class);
                    ClinicalDocument doc = documentStore.get(id);
                    if (doc == null) {
                        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                        HashMap<String, String> error = new HashMap<>();
                        error.put("error", "Document not found");
                        error.put("documentId", id);
                        exchange.getIn().setBody(error);
                    } else {
                        exchange.getIn().setBody(doc);
                    }
                })
                .marshal(jsonFormat);

        from("direct:health")
                .routeId("rest-health")
                .process(exchange -> {
                    HashMap<String, Object> health = new HashMap<>();
                    health.put("status", "UP");
                    health.put("patients", patientStore.size());
                    health.put("observations", observationStore.size());
                    health.put("documents", documentStore.size());
                    exchange.getIn().setBody(health);
                })
                .marshal(jsonFormat);

        // SEDA consumer: store data for REST queries (no-op since processors already store)
        from("seda:to-rest-store")
                .routeId("to-rest-store")
                .log("Data available via REST API for document ${header.documentId}");
    }
}
