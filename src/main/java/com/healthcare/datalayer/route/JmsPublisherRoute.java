package com.healthcare.datalayer.route;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.healthcare.datalayer.model.ClinicalDocument;

@ApplicationScoped
public class JmsPublisherRoute extends RouteBuilder {

    @ConfigProperty(name = "jms.queue.patients", defaultValue = "queue.patients")
    String patientsQueue;

    @ConfigProperty(name = "jms.queue.observations", defaultValue = "queue.observations")
    String observationsQueue;

    @ConfigProperty(name = "jms.topic.clinical-events", defaultValue = "topic.clinical-events")
    String clinicalEventsTopic;

    @Override
    public void configure() throws Exception {

        JacksonDataFormat jsonFormat = new JacksonDataFormat(ClinicalDocument.class);

        from("seda:to-jms")
                .routeId("jms-publisher")
                .log("Publishing document ${header.documentId} to JMS")
                .doTry()
                    .marshal(jsonFormat)
                    .toD("jms:queue:%s".formatted(patientsQueue))
                    .toD("jms:topic:%s".formatted(clinicalEventsTopic))
                    .log("Published to JMS queue '%s' and topic '%s'"
                            .formatted(patientsQueue, clinicalEventsTopic))
                .doCatch(Exception.class)
                    .log(LoggingLevel.WARN, "JMS publish failed — ${exception.message}")
                .end();
    }
}
