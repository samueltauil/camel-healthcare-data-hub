package com.healthcare.datalayer.route;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.BindyType;

import com.healthcare.datalayer.model.Patient;

/**
 * Loads bundled sample data into the in-memory stores on application startup.
 * This ensures the REST/SOAP endpoints serve data immediately,
 * even without an FTP server or file upload.
 */
@ApplicationScoped
public class SampleDataLoaderRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("timer:load-sample-data?repeatCount=1&delay=1000")
                .routeId("sample-data-loader")
                .log("Loading bundled sample patient data...")
                .setBody(constant(null))
                .pollEnrich("file:sample-data/csv?fileName=patients.csv&noop=true&idempotent=false", 5000)
                .choice()
                    .when(body().isNotNull())
                        .unmarshal().bindy(BindyType.Csv, Patient.class)
                        .process("csvPatientProcessor")
                        .log("Loaded ${header.recordCount} patients from bundled sample data")
                    .otherwise()
                        .log("No bundled sample data found at sample-data/csv/patients.csv — skipping")
                .end();
    }
}
