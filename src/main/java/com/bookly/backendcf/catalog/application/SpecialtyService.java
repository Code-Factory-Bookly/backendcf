package com.bookly.backendcf.catalog.application;

import com.bookly.backendcf.catalog.domain.model.Specialty;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.catalog.infrastructure.persistence.SpecialtyRepository;
import com.bookly.backendcf.catalog.presentation.dto.SpecialtyRequest;
import com.bookly.backendcf.catalog.presentation.dto.SpecialtyResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpecialtyService {

    private final SpecialtyRepository specialtyRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;

    public SpecialtyService(
            SpecialtyRepository specialtyRepository,
            ServiceOfferingRepository serviceOfferingRepository) {
        this.specialtyRepository = specialtyRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
    }

    @Transactional(readOnly = true)
    public List<SpecialtyResponse> list() {
        return specialtyRepository.findAllByOrderByNameAsc().stream()
                .map(SpecialtyResponse::from)
                .toList();
    }

    @Transactional
    public SpecialtyResponse create(SpecialtyRequest request) {
        String name = normalize(request.name());

        if (specialtyRepository.existsByNameIgnoreCase(name)) {
            throw new SpecialtyAlreadyExistsException(name);
        }

        Specialty specialty = new Specialty(name, normalizeOptional(request.description()));
        return SpecialtyResponse.from(specialtyRepository.save(specialty));
    }

    @Transactional
    public SpecialtyResponse update(UUID id, SpecialtyRequest request) {
        Specialty specialty = findSpecialty(id);
        String name = normalize(request.name());

        if (specialtyRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new SpecialtyAlreadyExistsException(name);
        }

        specialty.update(name, normalizeOptional(request.description()));
        return SpecialtyResponse.from(specialty);
    }

    /** Una especialidad con servicios no se borra: dejaría esos servicios huérfanos. */
    @Transactional
    public void delete(UUID id) {
        Specialty specialty = findSpecialty(id);

        if (serviceOfferingRepository.existsBySpecialtyId(id)) {
            throw new SpecialtyHasServicesException(id);
        }

        specialtyRepository.delete(specialty);
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
