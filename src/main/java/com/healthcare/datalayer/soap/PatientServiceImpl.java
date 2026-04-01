package com.healthcare.datalayer.soap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.jws.WebService;

import com.healthcare.datalayer.model.Patient;

/**
 * CDI-managed implementation of the {@link PatientService} SOAP web service.
 *
 * <p>Delegates all operations to the CDI-produced {@code patientStore} map, which is
 * populated at runtime by the Camel ingestion processors
 * ({@link com.healthcare.datalayer.processor.CsvPatientProcessor},
 * {@link com.healthcare.datalayer.processor.Hl7MessageProcessor},
 * {@link com.healthcare.datalayer.processor.SyntheaCsvProcessor}).</p>
 *
 * <p>Exposed via Quarkus CXF on port {@code PatientServicePort} under the
 * {@code http://healthcare.com/datalayer/soap} target namespace.</p>
 *
 * @see PatientService
 */
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

    /** {@inheritDoc} */
    @Override
    public Patient getPatient(String patientId) {
        return patientStore.get(patientId);
    }

    /** {@inheritDoc} */
    @Override
    public List<Patient> searchPatients(String lastName) {
        return patientStore.values().stream()
                .filter(p -> p.getLastName() != null
                        && p.getLastName().equalsIgnoreCase(lastName))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Patient> getAllPatients() {
        return new ArrayList<>(patientStore.values());
    }
}
