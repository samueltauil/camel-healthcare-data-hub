package com.healthcare.datalayer.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthcare.datalayer.model.ClinicalDocument;
import com.healthcare.datalayer.model.Patient;
import com.healthcare.datalayer.model.Observation;

/**
 * CDI application configuration that produces shared singleton beans used across
 * the healthcare data layer.
 *
 * <p>Beans produced here are injected by name into Camel processors and REST routes.
 * The three in-memory stores serve as the primary data repositories for the application,
 * while the {@link ObjectMapper} ensures consistent JSON serialization with Java 8+
 * date/time support.
 */
@ApplicationScoped
public class AppConfig {

    /**
     * Produces a Jackson {@link ObjectMapper} configured for healthcare JSON payloads.
     *
     * <p>Registers the {@link JavaTimeModule} so that {@code java.time} types
     * (e.g. {@code LocalDate}, {@code LocalDateTime}) are serialized as ISO-8601
     * strings rather than numeric timestamps. Used by Camel's JSON marshalling in
     * the JMS, Kafka, and REST routes.
     *
     * @return a singleton {@link ObjectMapper} available as {@code @Named("healthcareObjectMapper")}
     */
    @Produces
    @Singleton
    @Named("healthcareObjectMapper")
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Produces the in-memory patient store, keyed by patient ID.
     *
     * <p>Written to by ingestion processors ({@code CsvPatientProcessor},
     * {@code SyntheaCsvProcessor}, {@code Hl7MessageProcessor}) and read by
     * the REST API and {@code PatientServiceImpl}.
     *
     * @return a singleton {@link ConcurrentHashMap} available as {@code @Named("patientStore")}
     */
    @Produces
    @Singleton
    @Named("patientStore")
    public Map<String, Patient> patientStore() {
        return new ConcurrentHashMap<>();
    }

    /**
     * Produces the in-memory observation store, keyed by observation ID.
     *
     * <p>Read by the REST API and FHIR processing routes. Observations may be
     * populated by future ingestion processors or external integrations.
     *
     * @return a singleton {@link ConcurrentHashMap} available as {@code @Named("observationStore")}
     */
    @Produces
    @Singleton
    @Named("observationStore")
    public Map<String, Observation> observationStore() {
        return new ConcurrentHashMap<>();
    }

    /**
     * Produces the in-memory clinical document store, keyed by document ID.
     *
     * <p>Written to by all ingestion processors after assembling a
     * {@link ClinicalDocument} from an ingested file. Read by the REST API
     * to serve document metadata and contents.
     *
     * @return a singleton {@link ConcurrentHashMap} available as {@code @Named("documentStore")}
     */
    @Produces
    @Singleton
    @Named("documentStore")
    public Map<String, ClinicalDocument> documentStore() {
        return new ConcurrentHashMap<>();
    }
}
