
package com.bookly.backendcf.resource.application;

import com.bookly.backendcf.resource.domain.model.PhysicalResource;
import com.bookly.backendcf.resource.infrastructure.persistence.PhysicalResourceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhysicalResourceService {

    private final PhysicalResourceRepository repository;

    public PhysicalResourceService(PhysicalResourceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PhysicalResource> list() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public PhysicalResource get(UUID id) {
        return findResource(id);
    }

    @Transactional
    public PhysicalResource create(String name, int totalQuantity) {
        String normalizedName = normalize(name);

        validateName(normalizedName);

        if (repository.existsByNameIgnoreCase(normalizedName)) {
            throw new PhysicalResourceAlreadyExistsException(normalizedName);
        }

        PhysicalResource resource =
                new PhysicalResource(normalizedName, totalQuantity);

        return repository.save(resource);
    }

    @Transactional
    public PhysicalResource update(UUID id, String name, int totalQuantity) {
        PhysicalResource resource = findResource(id);
        String normalizedName = normalize(name);

        validateName(normalizedName);

        if (repository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new PhysicalResourceAlreadyExistsException(normalizedName);
        }

        resource.update(normalizedName, totalQuantity);

        return resource;
    }

    private PhysicalResource findResource(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new PhysicalResourceNotFoundException(id));
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ");
    }

    private void validateName(String name) {
        if (name.isBlank() || name.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "El nombre del recurso es obligatorio y no puede superar 150 caracteres");
        }
    }
}

