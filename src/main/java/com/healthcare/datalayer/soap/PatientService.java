package com.healthcare.datalayer.soap;

import java.util.List;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;

import com.healthcare.datalayer.model.Patient;

@WebService(
        name = "PatientService",
        targetNamespace = "http://healthcare.com/datalayer/soap"
)
public interface PatientService {

    @WebMethod(operationName = "getPatient")
    @WebResult(name = "patient")
    Patient getPatient(@WebParam(name = "patientId") String patientId);

    @WebMethod(operationName = "searchPatients")
    @WebResult(name = "patients")
    List<Patient> searchPatients(@WebParam(name = "lastName") String lastName);

    @WebMethod(operationName = "getAllPatients")
    @WebResult(name = "patients")
    List<Patient> getAllPatients();
}
