package com.healthcare.datalayer.soap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.jws.WebService;

import com.healthcare.datalayer.model.Patient;

@WebService(
        serviceName = "PatientService",
        portName = "PatientServicePort",
        targetNamespace = "http://healthcare.com/datalayer/soap",
        endpointInterface = "com.healthcare.datalayer.soap.PatientService"
)
@ApplicationScoped
public class PatientServiceImpl implements PatientService {

    @Inject
    @Named("patientStore")
    Map<String, Patient> patientStore;

    @Override
    public Patient getPatient(String patientId) {
        return patientStore.get(patientId);
    }

    @Override
    public List<Patient> searchPatients(String lastName) {
        return patientStore.values().stream()
                .filter(p -> p.getLastName() != null
                        && p.getLastName().equalsIgnoreCase(lastName))
                .toList();
    }

    @Override
    public List<Patient> getAllPatients() {
        return new ArrayList<>(patientStore.values());
    }
}
