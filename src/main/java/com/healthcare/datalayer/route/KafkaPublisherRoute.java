package com.healthcare.datalayer.route;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.healthcare.datalayer.model.ClinicalDocument;

/**
 * Outbound route that publishes clinical documents to Apache Kafka.
 *
 * <p>Uses the <strong>Camel Kafka component</strong> ({@code kafka:}) to produce messages
 * to a Kafka topic. The component wraps the standard Kafka Producer client and supports
 * all producer configuration options via URI parameters.</p>
 *
 * <h3>Kafka key strategy</h3>
 * <p>The message key is set to the {@code documentId} header via the {@code kafka.KEY}
 * Camel header. This ensures that all messages related to the same clinical document land
 * on the same Kafka partition, preserving per-document ordering. It also enables
 * log-compacted topics to retain only the latest version of each document.</p>
 *
 * <h3>Topic naming</h3>
 * <p>Topics follow a dot-separated namespace convention: {@code healthcare.patients.ingested}.
 * This makes it easy to apply ACLs, monitoring, and retention policies by namespace prefix.</p>
 *
 * <h3>Routes defined</h3>
 * <ul>
 *   <li><strong>kafka-publisher</strong> — consumes from {@code seda:to-kafka}, marshals
 *       the {@code ClinicalDocument} to JSON, sets the Kafka record key, and produces to
 *       the configured topic.</li>
 * </ul>
 *
 * <h3>Key patterns</h3>
 * <ul>
 *   <li><em>doTry / doCatch</em> — wraps the Kafka produce call so that broker connectivity
 *       or serialization errors are logged as warnings without failing the entire fan-out
 *       exchange.</li>
 * </ul>
 *
 * <h3>Pipeline position</h3>
 * <p>This is one of five <strong>outbound connectors</strong> in the fan-out from
 * {@link FtpPollingRoute}.</p>
 */
@ApplicationScoped
public class KafkaPublisherRoute extends RouteBuilder {

    /** Kafka topic for successfully ingested patient documents. */
    @ConfigProperty(name = "kafka.topic.patients", defaultValue = "healthcare.patients.ingested")
    String patientsTopic;

    /** Kafka topic for error/dead-letter events (reserved for future use). */
    @ConfigProperty(name = "kafka.topic.errors", defaultValue = "healthcare.errors")
    String errorsTopic;

    /** Comma-separated list of Kafka broker addresses for initial cluster discovery. */
    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "localhost:9092")
    String bootstrapServers;

    @Override
    public void configure() throws Exception {

        JacksonDataFormat jsonFormat = new JacksonDataFormat(ClinicalDocument.class);

        /*
         * Kafka publisher route.
         *
         * 1. Marshal the ClinicalDocument to JSON.
         * 2. Set the "kafka.KEY" header to the documentId — this determines the
         *    Kafka partition for the record. Using documentId as the key guarantees
         *    that updates for the same document are ordered within a partition, and
         *    enables log compaction to keep only the latest state.
         * 3. Produce to the patients topic via toD() (dynamic URI) so that the topic
         *    name and broker list can be changed at runtime via configuration.
         * 4. doTry/doCatch isolates Kafka failures from the other fan-out connectors.
         */
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
