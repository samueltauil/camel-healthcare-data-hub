package com.healthcare.datalayer.soap;

import java.util.List;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;

import com.healthcare.datalayer.model.Patient;

/**
 * JAX-WS service interface exposing patient lookup operations over SOAP.
 *
 * <p>Published at namespace {@code http://healthcare.com/datalayer/soap} and backed by
 * {@link PatientServiceImpl}. The underlying data source is the in-memory
 * {@code patientStore} populated by the various Camel ingestion routes.</p>
 *
 * <p>Operations:</p>
 * <ul>
 *   <li>{@code getPatient}     — retrieve a single patient by ID</li>
 *   <li>{@code searchPatients} — find patients by last name (case-insensitive)</li>
 *   <li>{@code getAllPatients}  — list every patient currently in the store</li>
 * </ul>
 *
 * @see PatientServiceImpl
 */
@WebService(
        name = "PatientService",
        targetNamespace = "http://healthcare.com/datalayer/soap"
)
public interface PatientService {

    /**
     * Retrieves a single patient by their unique identifier.
     *
     * @param patientId the unique patient identifier (e.g. from PID-3 or CSV column)
     * @return the matching {@link Patient}, or {@code null} if not found
     */
    @WebMethod(operationName = "getPatient")
    @WebResult(name = "patient")
    Patient getPatient(@WebParam(name = "patientId") String patientId);

    /**
     * Searches for patients whose last name matches the given value (case-insensitive).
     *
     * @param lastName the last name to search for
     * @return a list of matching {@link Patient} records; empty if none match
     */
    @WebMethod(operationName = "searchPatients")
    @WebResult(name = "patients")
    List<Patient> searchPatients(@WebParam(name = "lastName") String lastName);

    /**
     * Returns all patients currently held in the in-memory store.
     *
     * @return a list of all {@link Patient} records
     */
    @WebMethod(operationName = "getAllPatients")
    @WebResult(name = "patients")
    List<Patient> getAllPatients();
}
