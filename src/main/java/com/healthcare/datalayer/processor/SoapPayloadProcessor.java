package com.healthcare.datalayer.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.healthcare.datalayer.model.ClinicalDocument;
import com.healthcare.datalayer.model.Patient;

@ApplicationScoped
@Named("soapPayloadProcessor")
public class SoapPayloadProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(SoapPayloadProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();

        if (body instanceof Patient patient) {
            exchange.getIn().setBody(toSoapXml(patient));
        } else if (body instanceof ClinicalDocument document) {
            StringBuilder sb = new StringBuilder();
            sb.append("<PatientList xmlns=\"http://healthcare.com/datalayer/soap\">\n");
            for (Patient p : document.getPatients()) {
                sb.append(toSoapXml(p)).append("\n");
            }
            sb.append("</PatientList>");
            exchange.getIn().setBody(sb.toString());
        }

        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/xml");
        LOG.debug("Transformed to SOAP XML payload");
    }

    private String toSoapXml(Patient patient) {
        return """
                <Patient xmlns="http://healthcare.com/datalayer/soap">
                    <patientId>%s</patientId>
                    <firstName>%s</firstName>
                    <lastName>%s</lastName>
                    <dateOfBirth>%s</dateOfBirth>
                    <gender>%s</gender>
                    <address>
                        <line>%s</line>
                        <city>%s</city>
                        <state>%s</state>
                        <zipCode>%s</zipCode>
                    </address>
                    <phoneNumber>%s</phoneNumber>
                    <email>%s</email>
                    <insuranceId>%s</insuranceId>
                    <primaryCareProvider>%s</primaryCareProvider>
                </Patient>
                """.formatted(
                nullSafe(patient.getPatientId()),
                nullSafe(patient.getFirstName()),
                nullSafe(patient.getLastName()),
                patient.getDateOfBirth() != null ? patient.getDateOfBirth().toString() : "",
                nullSafe(patient.getGender()),
                nullSafe(patient.getAddressLine1()),
                nullSafe(patient.getCity()),
                nullSafe(patient.getState()),
                nullSafe(patient.getZipCode()),
                nullSafe(patient.getPhoneNumber()),
                nullSafe(patient.getEmail()),
                nullSafe(patient.getInsuranceId()),
                nullSafe(patient.getPrimaryCareProvider()));
    }

    private String nullSafe(String value) {
        return value != null ? escapeXml(value) : "";
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
