package com.healthcare.datalayer.route;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;

/**
 * Integration bridge between the SOAP web-service layer and the Camel routing engine.
 *
 * <p>The primary SOAP endpoint is exposed and managed by <strong>Quarkus CXF</strong>
 * (via {@code PatientServiceImpl}), which handles WSDL generation, XML binding, and
 * WS-* protocol details. This Camel route does <em>not</em> host the SOAP endpoint
 * itself; instead it provides a {@code direct:} entry point that the CXF service
 * implementation can call into when it needs Camel-mediated processing — for example,
 * data enrichment, transformation, or additional routing logic that benefits from the
 * Camel EIP toolkit.</p>
 *
 * <h3>Routes defined</h3>
 * <ul>
 *   <li><strong>soap-patient-lookup</strong> — accepts a patient lookup request
 *       (typically invoked from {@code PatientServiceImpl}), delegates to the
 *       {@code soapPayloadProcessor} bean to build the SOAP response payload,
 *       and returns the result to the caller via the synchronous {@code direct:}
 *       channel.</li>
 * </ul>
 *
 * <h3>Pipeline position</h3>
 * <p>This route is an <strong>optional query path</strong> — it does not participate
 * in the ingest fan-out. External SOAP clients invoke the CXF endpoint, which in turn
 * may delegate to this route for lookup logic.</p>
 */
@ApplicationScoped
public class SoapEndpointRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        /*
         * Synchronous patient lookup route invoked from the CXF SOAP service.
         * The "direct:" component provides an in-process, synchronous call so the
         * CXF service can obtain a response within the same request thread.
         * soapPayloadProcessor builds the response DTO from the in-memory stores.
         */
        from("direct:soap-patient-lookup")
                .routeId("soap-patient-lookup")
                .log("SOAP patient lookup request received for: ${header.patientId}")
                .process("soapPayloadProcessor")
                .log("SOAP response generated");
    }
}
