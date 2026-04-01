package com.healthcare.datalayer.route;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.healthcare.datalayer.model.ClinicalDocument;

/**
 * Outbound route that publishes clinical documents to JMS destinations (queues and topics).
 *
 * <p>Uses the <strong>Camel JMS component</strong> ({@code jms:}) backed by an
 * <strong>Apache Artemis</strong> broker. Artemis supports auto-creation of destinations,
 * so the configured queues and topics are created on first use without requiring manual
 * broker administration.</p>
 *
 * <h3>Queue vs. Topic</h3>
 * <ul>
 *   <li><strong>Queue</strong> ({@code jms:queue:…}) — point-to-point messaging. Each
 *       message is consumed by exactly one consumer, making it suitable for command-style
 *       processing (e.g., a downstream patient-indexing service).</li>
 *   <li><strong>Topic</strong> ({@code jms:topic:…}) — publish/subscribe messaging. Every
 *       active subscriber receives a copy of the message, making it suitable for event
 *       notification (e.g., "a new clinical document was ingested").</li>
 * </ul>
 *
 * <h3>Routes defined</h3>
 * <ul>
 *   <li><strong>jms-publisher</strong> — consumes from {@code seda:to-jms}, marshals the
 *       {@code ClinicalDocument} to JSON, and sends it to both a point-to-point queue
 *       and a pub/sub topic.</li>
 * </ul>
 *
 * <h3>Key patterns</h3>
 * <ul>
 *   <li><em>doTry / doCatch</em> — wraps the JMS send so that broker connectivity issues
 *       are logged as warnings rather than propagating to the fan-out multicast.</li>
 * </ul>
 *
 * <h3>Pipeline position</h3>
 * <p>This is one of five <strong>outbound connectors</strong> in the fan-out from
 * {@link FtpPollingRoute}.</p>
 */
@ApplicationScoped
public class JmsPublisherRoute extends RouteBuilder {

    /** JMS queue name for patient data (point-to-point delivery). */
    @ConfigProperty(name = "jms.queue.patients", defaultValue = "queue.patients")
    String patientsQueue;

    /** JMS queue name for observation data (currently unused in the route but reserved). */
    @ConfigProperty(name = "jms.queue.observations", defaultValue = "queue.observations")
    String observationsQueue;

    /** JMS topic name for clinical event notifications (pub/sub delivery). */
    @ConfigProperty(name = "jms.topic.clinical-events", defaultValue = "topic.clinical-events")
    String clinicalEventsTopic;

    @Override
    public void configure() throws Exception {

        JacksonDataFormat jsonFormat = new JacksonDataFormat(ClinicalDocument.class);

        /*
         * JMS publisher route.
         *
         * 1. Marshal the ClinicalDocument to JSON using Jackson.
         * 2. Send to the patients queue (point-to-point) — a single downstream
         *    consumer will process each message.
         * 3. Send to the clinical-events topic (pub/sub) — all subscribers receive
         *    a copy for event-driven workflows.
         * 4. doTry/doCatch prevents broker failures from affecting the other
         *    fan-out connectors (Kafka, FHIR, MLLP, REST).
         *
         * Artemis auto-create: the configured queue and topic names are created
         * automatically by the broker on first use if they do not already exist.
         */
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
