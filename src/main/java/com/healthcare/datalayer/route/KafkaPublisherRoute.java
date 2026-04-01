package com.healthcare.datalayer.route;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.healthcare.datalayer.model.ClinicalDocument;

@ApplicationScoped
public class KafkaPublisherRoute extends RouteBuilder {

    @ConfigProperty(name = "kafka.topic.patients", defaultValue = "healthcare.patients.ingested")
    String patientsTopic;

    @ConfigProperty(name = "kafka.topic.errors", defaultValue = "healthcare.errors")
    String errorsTopic;

    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "localhost:9092")
    String bootstrapServers;

    @Override
    public void configure() throws Exception {

        JacksonDataFormat jsonFormat = new JacksonDataFormat(ClinicalDocument.class);

        from("seda:to-kafka")
                .routeId("kafka-publisher")
                .log("Publishing document ${header.documentId} to Kafka")
                .doTry()
                    .marshal(jsonFormat)
                    .setHeader("kafka.KEY", simple("${header.documentId}"))
                    .toD("kafka:%s?brokers=%s".formatted(patientsTopic, bootstrapServers))
                    .log("Published to Kafka topic '%s'".formatted(patientsTopic))
                .doCatch(Exception.class)
                    .log(LoggingLevel.WARN, "Kafka publish failed — ${exception.message}")
                .end();
    }
}
