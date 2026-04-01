package com.healthcare.datalayer.processor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.healthcare.datalayer.model.ClinicalDocument;
import com.healthcare.datalayer.model.Patient;

import static org.junit.jupiter.api.Assertions.*;

class CsvPatientProcessorTest {

    private CsvPatientProcessor processor;
    private Map<String, Patient> patientStore;
    private Map<String, ClinicalDocument> documentStore;

    @BeforeEach
    void setUp() throws Exception {
        patientStore = new ConcurrentHashMap<>();
        documentStore = new ConcurrentHashMap<>();

        processor = new CsvPatientProcessor();

        // Inject stores via reflection (for unit testing without CDI)
        var patientStoreField = CsvPatientProcessor.class.getDeclaredField("patientStore");
        patientStoreField.setAccessible(true);
        patientStoreField.set(processor, patientStore);

        var documentStoreField = CsvPatientProcessor.class.getDeclaredField("documentStore");
        documentStoreField.setAccessible(true);
        documentStoreField.set(processor, documentStore);
    }

    @Test
    void testProcessCsvPatients() throws Exception {
        Patient p1 = new Patient();
        p1.setPatientId("P001");
        p1.setFirstName("John");
        p1.setLastName("Smith");
        p1.setDateOfBirth(LocalDate.of(1985, 3, 15));
        p1.setGender("M");

        Patient p2 = new Patient();
        p2.setPatientId("P002");
        p2.setFirstName("Jane");
        p2.setLastName("Doe");
        p2.setDateOfBirth(LocalDate.of(1990, 7, 22));
        p2.setGender("F");

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(Exchange.FILE_NAME, "test-patients.csv");
        exchange.getIn().setBody(List.of(p1, p2));

        processor.process(exchange);

        assertEquals(2, patientStore.size());
        assertEquals(1, documentStore.size());

        ClinicalDocument doc = exchange.getIn().getBody(ClinicalDocument.class);
        assertNotNull(doc);
        assertEquals("CSV", doc.getSourceFormat());
        assertEquals(2, doc.getPatients().size());
        assertEquals("test-patients.csv", doc.getSourceFile());

        assertEquals(2, exchange.getIn().getHeader("recordCount", Integer.class));
    }

    @Test
    void testPatientStoredById() throws Exception {
        Patient p1 = new Patient();
        p1.setPatientId("P999");
        p1.setFirstName("Test");
        p1.setLastName("Patient");

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(Exchange.FILE_NAME, "test.csv");
        exchange.getIn().setBody(List.of(p1));

        processor.process(exchange);

        assertTrue(patientStore.containsKey("P999"));
        assertEquals("Test", patientStore.get("P999").getFirstName());
    }

    @Test
    void testEmptyPatientList() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(Exchange.FILE_NAME, "empty.csv");
        exchange.getIn().setBody(List.of());

        processor.process(exchange);

        assertEquals(0, patientStore.size());
        assertEquals(1, documentStore.size());
        assertEquals(0, exchange.getIn().getHeader("recordCount", Integer.class));
    }
}
