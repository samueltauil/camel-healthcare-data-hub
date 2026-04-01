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

@ApplicationScoped
public class AppConfig {

    @Produces
    @Singleton
    @Named("healthcareObjectMapper")
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Produces
    @Singleton
    @Named("patientStore")
    public Map<String, Patient> patientStore() {
        return new ConcurrentHashMap<>();
    }

    @Produces
    @Singleton
    @Named("observationStore")
    public Map<String, Observation> observationStore() {
        return new ConcurrentHashMap<>();
    }

    @Produces
    @Singleton
    @Named("documentStore")
    public Map<String, ClinicalDocument> documentStore() {
        return new ConcurrentHashMap<>();
    }
}
