package com.healthcare.datalayer.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.healthcare.datalayer.model.ClinicalDocument;
import com.healthcare.datalayer.model.Patient;

/**
 * Camel {@link Processor} that transforms {@link Patient} or {@link ClinicalDocument}
 * exchange bodies into SOAP-compatible XML payloads.
 *
 * <p><b>Route:</b> Used in the {@code soap-patient-lookup} route defined in
 * {@link com.healthcare.datalayer.route.SoapEndpointRoute}. Provides a Camel-mediated
 * bridge for SOAP processing when additional transformation or routing logic is needed
 * beyond the direct CXF endpoint.</p>
 *
 * <h3>Exchange contract</h3>
 * <ul>
 *   <li><b>Input body:</b> either a single {@link Patient} or a {@link ClinicalDocument}
 *       containing multiple patients</li>
 *   <li><b>Output body:</b> XML string — a single {@code <Patient>} element or a
 *       {@code <PatientList>} wrapper, in the {@code http://healthcare.com/datalayer/soap}
 *       namespace</li>
 *   <li><b>Output header:</b> {@code Content-Type} set to {@code application/xml}</li>
 * </ul>
 *
 * <p>All patient field values are XML-escaped to prevent injection of invalid XML
 * characters ({@code &, <, >, ", '}).</p>
 *
 * @see com.healthcare.datalayer.route.SoapEndpointRoute
 */
@ApplicationScoped
@Named("soapPayloadProcessor")
public class SoapPayloadProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(SoapPayloadProcessor.class);

    /**
     * Transforms the exchange body into a SOAP XML payload.
     *
     * <ol>
     *   <li>Inspects the body type: {@link Patient} or {@link ClinicalDocument}.</li>
     *   <li>For a single {@link Patient}, serializes it as a {@code <Patient>} XML element.</li>
     *   <li>For a {@link ClinicalDocument}, wraps all patients in a {@code <PatientList>}
     *       element, serializing each as a {@code <Patient>} child.</li>
     *   <li>Sets the {@code Content-Type} header to {@code application/xml}.</li>
     * </ol>
     *
     * @param exchange the Camel exchange; body must be a {@link Patient} or
     *                 {@link ClinicalDocument}
     * @throws Exception if serialization fails
     */
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

    /**
     * Serializes a single {@link Patient} into a SOAP-namespaced XML {@code <Patient>} element.
     *
     * <p>All string fields are passed through {@link #nullSafe} to handle {@code null}
     * values and escape XML special characters.</p>
     *
     * @param patient the patient to serialize
     * @return an XML string representing the patient
     */
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

    /**
     * Returns an XML-safe string for the given value, substituting an empty string
     * if the value is {@code null}.
     *
     * @param value the string value (may be {@code null})
     * @return the XML-escaped value, or {@code ""} if {@code null}
     */
    private String nullSafe(String value) {
        return value != null ? escapeXml(value) : "";
    }

    /**
     * Escapes the five XML special characters ({@code &, <, >, ", '}) in the given
     * string to their corresponding XML entities.
     *
     * @param value the raw string to escape (must not be {@code null})
     * @return the XML-escaped string
     */
    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
