package com.healthcare.datalayer.route;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class SoapEndpointRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // The SOAP endpoint is handled by Quarkus CXF directly via PatientServiceImpl.
        // This route provides an integration bridge for Camel-mediated SOAP processing
        // if additional transformation or routing logic is needed.

        from("direct:soap-patient-lookup")
                .routeId("soap-patient-lookup")
                .log("SOAP patient lookup request received for: ${header.patientId}")
                .process("soapPayloadProcessor")
                .log("SOAP response generated");
    }
}
