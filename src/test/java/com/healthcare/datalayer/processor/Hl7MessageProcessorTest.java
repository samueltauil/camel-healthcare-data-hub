package com.healthcare.datalayer.processor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;

import com.healthcare.datalayer.model.ClinicalDocument;
import com.healthcare.datalayer.model.Patient;

import static org.junit.jupiter.api.Assertions.*;

class Hl7MessageProcessorTest {

    private Hl7MessageProcessor processor;
    private Map<String, Patient> patientStore;
    private Map<String, ClinicalDocument> documentStore;

    private static final String ADT_A01_MESSAGE =
            "MSH|^~\\&|TEST_APP|TEST_FAC|RCV_APP|RCV_FAC|20260101120000||ADT^A01|MSG001|P|2.5\r"
            + "EVN|A01|20260101120000\r"
            + "PID|1||P100^^^TEST&2.16.840.1.113883.19.5&ISO||User^Test^M||20000101|M|||1 Test St^^TestCity^IL^60000||555-000-0001||S||999-99-9999\r"
            + "PV1|1|I|W^100^1^TEST||||99999^Test^Dr|||MED||||7|A0||\r";

    @BeforeEach
    void setUp() throws Exception {
        patientStore = new ConcurrentHashMap<>();
        documentStore = new ConcurrentHashMap<>();

        processor = new Hl7MessageProcessor();

        var patientStoreField = Hl7MessageProcessor.class.getDeclaredField("patientStore");
        patientStoreField.setAccessible(true);
        patientStoreField.set(processor, patientStore);

        var documentStoreField = Hl7MessageProcessor.class.getDeclaredField("documentStore");
        documentStoreField.setAccessible(true);
        documentStoreField.set(processor, documentStore);
    }

    @Test
    void testParseAdtA01() throws Exception {
        HapiContext context = new DefaultHapiContext();
        PipeParser parser = context.getPipeParser();
        Message message = parser.parse(ADT_A01_MESSAGE);

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(Exchange.FILE_NAME, "test-adt.hl7");
        exchange.getIn().setBody(message);

        processor.process(exchange);

        assertEquals(1, patientStore.size());
        assertTrue(patientStore.containsKey("P100"));

        Patient patient = patientStore.get("P100");
        assertEquals("Test", patient.getFirstName());
        assertEquals("User", patient.getLastName());
        assertEquals("M", patient.getGender());
        assertEquals("TestCity", patient.getCity());
        assertEquals("IL", patient.getState());

        ClinicalDocument doc = exchange.getIn().getBody(ClinicalDocument.class);
        assertNotNull(doc);
        assertEquals("HL7v2", doc.getSourceFormat());
        assertEquals(1, doc.getPatients().size());

        context.close();
    }

    @Test
    void testOriginalHl7MessagePreserved() throws Exception {
        HapiContext context = new DefaultHapiContext();
        PipeParser parser = context.getPipeParser();
        Message message = parser.parse(ADT_A01_MESSAGE);

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(Exchange.FILE_NAME, "test-adt.hl7");
        exchange.getIn().setBody(message);

        processor.process(exchange);

        assertNotNull(exchange.getIn().getHeader("originalHl7Message"));
        assertEquals("HL7v2", exchange.getIn().getHeader("sourceFormat"));

        context.close();
    }
}
