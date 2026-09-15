package com.bookly.backendcf.catalog.application;

import com.bookly.backendcf.catalog.domain.model.ServiceOffering;
import com.bookly.backendcf.catalog.domain.model.Specialty;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.catalog.infrastructure.persistence.SpecialtyRepository;
import com.bookly.backendcf.catalog.presentation.dto.ServiceOfferingRequest;
import com.bookly.backendcf.catalog.presentation.dto.ServiceOfferingResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceOfferingService {

    private final ServiceOfferingRepository serviceOfferingRepository;
    private final SpecialtyRepository specialtyRepository;

    public ServiceOfferingService(
            ServiceOfferingRepository serviceOfferingRepository,
            SpecialtyRepository specialtyRepository) {
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.specialtyRepository = specialtyRepository;
    }

    @Transactional(readOnly = true)
    public List<ServiceOfferingResponse> list(UUID specialtyId) {
        List<ServiceOffering> offerings = specialtyId == null
                ? serviceOfferingRepository.findAllByOrderByNameAsc()
                : serviceOfferingRepository.findAllBySpecialtyIdOrderByNameAsc(specialtyId);

        return offerings.stream()
                .map(ServiceOfferingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceOfferingResponse get(UUID id) {
        return ServiceOfferingResponse.from(findOffering(id));
    }

    @Transactional
    public ServiceOfferingResponse create(ServiceOfferingRequest request) {
        Specialty specialty = findSpecialty(request.specialtyId());
        String name = normalize(request.name());

        if (serviceOfferingRepository.existsBySpecialtyIdAndNameIgnoreCase(specialty.getId(), name)) {
            throw new ServiceOfferingAlreadyExistsException(name);
        }

        ServiceOffering offering = new ServiceOffering(
                specialty,
                name,
                normalizeOptional(request.description()),
                request.durationMinutes(),
                request.price());

        return ServiceOfferingResponse.from(serviceOfferingRepository.save(offering));
    }

    @Transactional
    public ServiceOfferingResponse update(UUID id, ServiceOfferingRequest request) {
        ServiceOffering offering = findOffering(id);
        Specialty specialty = findSpecialty(request.specialtyId());
        String name = normalize(request.name());

        if (serviceOfferingRepository.existsBySpecialtyIdAndNameIgnoreCaseAndIdNot(specialty.getId(), name, id)) {
            throw new ServiceOfferingAlreadyExistsException(name);
        }

        offering.update(
                specialty,
                name,
                normalizeOptional(request.description()),
                request.durationMinutes(),
                request.price());

        return ServiceOfferingResponse.from(offering);
    }

    @Transactional
    public void delete(UUID id) {
        serviceOfferingRepository.delete(findOffering(id));
    }

    private ServiceOffering findOffering(UUID id) {
        return serviceOfferingRepository.findById(id).orElseThrow(() -> new ServiceOfferingNotFoundException(id));
    }

    private Specialty findSpecialty(UUID id) {
        return specialtyRepository.findById(id).orElseThrow(() -> new SpecialtyNotFoundException(id));
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : normalize(value);
    }
}
