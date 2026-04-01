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

/**
 * REST API route that exposes ingested healthcare data over HTTP.
 *
 * <p>Uses the <strong>Camel REST DSL</strong> backed by the {@code platform-http} component
 * (Quarkus / Vert.x). The REST DSL provides a declarative way to define REST services that
 * are then mapped to standard Camel routes via {@code direct:} endpoints.</p>
 *
 * <h3>Why {@code RestBindingMode.off}?</h3>
 * <p>Binding mode is set to {@link RestBindingMode#off} intentionally. This disables Camel's
 * automatic JSON marshalling/unmarshalling so that we can use an explicitly configured
 * {@link JacksonDataFormat} with the {@link JavaTimeModule} registered. This gives full
 * control over serialization — in particular, {@code java.time.*} types are rendered as
 * ISO-8601 strings instead of numeric timestamps.</p>
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <tr><th>Verb</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/patients</td><td>List all ingested patients</td></tr>
 *   <tr><td>GET</td><td>/api/patients/{patientId}</td><td>Get a single patient by ID</td></tr>
 *   <tr><td>GET</td><td>/api/observations</td><td>List all observations</td></tr>
 *   <tr><td>GET</td><td>/api/observations/patient/{patientId}</td><td>Observations for a patient</td></tr>
 *   <tr><td>GET</td><td>/api/documents</td><td>List all ingested clinical documents</td></tr>
 *   <tr><td>GET</td><td>/api/documents/{documentId}</td><td>Get a clinical document by ID</td></tr>
 *   <tr><td>GET</td><td>/api/health</td><td>Health check with store sizes</td></tr>
 * </table>
 *
 * <h3>Pipeline position</h3>
 * <p>This route sits at the <strong>read/query</strong> side of the pipeline. The
 * {@code seda:to-rest-store} consumer receives fan-out messages from
 * {@link FtpPollingRoute} and confirms that the data is queryable. The actual storage
 * happens in the upstream processors that populate the in-memory stores.</p>
 */
@ApplicationScoped
public class RestApiRoute extends RouteBuilder {

    /** In-memory map of patientId → Patient, populated by upstream processors. */
    @Inject
    @Named("patientStore")
    Map<String, Patient> patientStore;

    /** In-memory map of observationId → Observation. */
    @Inject
    @Named("observationStore")
    Map<String, Observation> observationStore;

    /** In-memory map of documentId → ClinicalDocument. */
    @Inject
    @Named("documentStore")
    Map<String, ClinicalDocument> documentStore;

    @Override
    public void configure() throws Exception {

        /*
         * REST configuration.
         * - "platform-http" delegates HTTP handling to the Quarkus/Vert.x server.
         * - bindingMode OFF: we marshal to JSON explicitly with a custom ObjectMapper
         *   that handles java.time types as ISO-8601 strings (see jsonFormat below).
         * - contextPath "/api" prefixes all REST endpoints.
         * - apiContextPath "/openapi" exposes the auto-generated OpenAPI spec.
         */
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

        /*
         * Custom ObjectMapper with JavaTimeModule so that LocalDate, Instant, etc.
         * are serialized as ISO-8601 strings rather than numeric arrays/timestamps.
         * This is the reason RestBindingMode.off is used above — Camel's auto-binding
         * would create its own ObjectMapper without this module.
         */
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        JacksonDataFormat jsonFormat = new JacksonDataFormat();
        jsonFormat.setObjectMapper(mapper);

        // Returns all patients as a JSON array
        from("direct:get-all-patients")
                .routeId("rest-get-all-patients")
                .process(exchange -> exchange.getIn().setBody(new ArrayList<>(patientStore.values())))
                .marshal(jsonFormat);

        // Looks up a single patient by the {patientId} path parameter; returns 404 if not found
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

        // Returns all observations as a JSON array
        from("direct:get-all-observations")
                .routeId("rest-get-all-observations")
                .process(exchange -> exchange.getIn().setBody(new ArrayList<>(observationStore.values())))
                .marshal(jsonFormat);

        // Filters observations by the {patientId} path parameter and returns matches
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

        // Returns all clinical documents as a JSON array
        from("direct:get-all-documents")
                .routeId("rest-get-all-documents")
                .process(exchange -> exchange.getIn().setBody(new ArrayList<>(documentStore.values())))
                .marshal(jsonFormat);

        // Looks up a single document by {documentId}; returns 404 if not found
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

        // Returns a simple health payload with the current store sizes
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

        /*
         * SEDA consumer for the fan-out channel.
         * This is a no-op acknowledgement route: the upstream processors have already
         * stored data in the in-memory maps, so the route simply logs that the data is
         * now available for REST queries.
         */
        from("seda:to-rest-store")
                .routeId("to-rest-store")
                .log("Data available via REST API for document ${header.documentId}");
    }
}
