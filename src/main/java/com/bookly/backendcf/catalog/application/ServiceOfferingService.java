package com.bookly.backendcf.catalog.application;

import com.bookly.backendcf.catalog.domain.model.ServiceOffering;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.catalog.presentation.dto.ServiceOfferingRequest;
import com.bookly.backendcf.catalog.presentation.dto.ServiceOfferingResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceOfferingService {

    private final ServiceOfferingRepository serviceOfferingRepository;

    public ServiceOfferingService(ServiceOfferingRepository serviceOfferingRepository) {
        this.serviceOfferingRepository = serviceOfferingRepository;
    }

    @Transactional(readOnly = true)
    public List<ServiceOfferingResponse> list(String category) {
        List<ServiceOffering> offerings = (category == null || category.isBlank())
                ? serviceOfferingRepository.findAllByOrderByNameAsc()
                : serviceOfferingRepository.findAllByCategoryIgnoreCaseOrderByNameAsc(category.trim());

        return offerings.stream()
                .map(ServiceOfferingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceOfferingResponse get(UUID id) {
        return ServiceOfferingResponse.from(findOffering(id));
    }

    /** El estado nace en ACTIVO: lo asigna el sistema, se ignora cualquier valor recibido al crear. */
    @Transactional
    public ServiceOfferingResponse create(ServiceOfferingRequest request) {
        String name = normalize(request.name());

        if (serviceOfferingRepository.existsByNameIgnoreCase(name)) {
            throw new ServiceOfferingAlreadyExistsException(name);
        }

        ServiceOffering offering = new ServiceOffering(
                name,
                normalizeOptional(request.description()),
                normalize(request.category()),
                request.durationMinutes(),
                request.price());

        return ServiceOfferingResponse.from(serviceOfferingRepository.save(offering));
    }

    @Transactional
    public ServiceOfferingResponse update(UUID id, ServiceOfferingRequest request) {
        ServiceOffering offering = findOffering(id);
        String name = normalize(request.name());

        if (serviceOfferingRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ServiceOfferingAlreadyExistsException(name);
        }

        offering.update(
                name,
                normalizeOptional(request.description()),
                normalize(request.category()),
                request.durationMinutes(),
                request.price(),
                request.status());

        return ServiceOfferingResponse.from(offering);
    }

    @Transactional
    public void delete(UUID id) {
        serviceOfferingRepository.delete(findOffering(id));
    }

    private ServiceOffering findOffering(UUID id) {
        return serviceOfferingRepository.findById(id).orElseThrow(() -> new ServiceOfferingNotFoundException(id));
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : normalize(value);
    }
}
