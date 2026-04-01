package com.healthcare.datalayer.route;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.healthcare.datalayer.model.Patient;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.time.LocalDate;

@QuarkusTest
class RestApiRouteTest {

    @Inject
    @Named("patientStore")
    Map<String, Patient> patientStore;

    @BeforeEach
    void setUp() {
        patientStore.clear();
    }

    @Test
    void testHealthEndpoint() {
        given()
                .when().get("/api/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void testGetAllPatientsEmpty() {
        given()
                .when().get("/api/patients")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void testGetAllPatientsWithData() {
        Patient p = new Patient();
        p.setPatientId("P001");
        p.setFirstName("John");
        p.setLastName("Smith");
        p.setDateOfBirth(LocalDate.of(1985, 3, 15));
        p.setGender("M");
        patientStore.put("P001", p);

        given()
                .when().get("/api/patients")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].patientId", equalTo("P001"))
                .body("[0].firstName", equalTo("John"));
    }

    @Test
    void testGetPatientById() {
        Patient p = new Patient();
        p.setPatientId("P002");
        p.setFirstName("Jane");
        p.setLastName("Doe");
        patientStore.put("P002", p);

        given()
                .when().get("/api/patients/P002")
                .then()
                .statusCode(200)
                .body("patientId", equalTo("P002"))
                .body("firstName", equalTo("Jane"));
    }

    @Test
    void testGetPatientNotFound() {
        given()
                .when().get("/api/patients/NONEXISTENT")
                .then()
                .statusCode(404)
                .body("error", equalTo("Patient not found"));
    }
}
