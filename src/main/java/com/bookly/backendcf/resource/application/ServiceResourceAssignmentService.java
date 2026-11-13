
package com.bookly.backendcf.resource.application;

import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.resource.domain.model.ServiceResource;
import com.bookly.backendcf.resource.domain.model.ServiceResourceId;
import com.bookly.backendcf.resource.infrastructure.persistence.PhysicalResourceRepository;
import com.bookly.backendcf.resource.infrastructure.persistence.ServiceResourceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServiceResourceAssignmentService {

    private final ServiceResourceRepository assignmentRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final PhysicalResourceRepository resourceRepository;

    public ServiceResourceAssignmentService(
            ServiceResourceRepository assignmentRepository,
            ServiceOfferingRepository serviceRepository,
            PhysicalResourceRepository resourceRepository) {
        this.assignmentRepository = assignmentRepository;
        this.serviceRepository = serviceRepository;
        this.resourceRepository = resourceRepository;
    }

    @Transactional
    public void assign(UUID serviceId, UUID resourceId) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No existe el servicio solicitado");
        }

        if (!resourceRepository.existsById(resourceId)) {
            throw new PhysicalResourceNotFoundException(resourceId);
        }

        if (assignmentRepository.existsByServiceIdAndResourceId(
                serviceId, resourceId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El recurso ya está asociado a este servicio");
        }

        assignmentRepository.save(new ServiceResource(serviceId, resourceId));
    }

    @Transactional(readOnly = true)
    public List<UUID> listResources(UUID serviceId) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No existe el servicio solicitado");
        }

        return assignmentRepository.findByServiceId(serviceId)
                .stream()
                .map(ServiceResource::getResourceId)
                .toList();
    }

    @Transactional
    public void remove(UUID serviceId, UUID resourceId) {
        ServiceResourceId id = new ServiceResourceId(serviceId, resourceId);

        if (!assignmentRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No existe la asociación entre el servicio y el recurso");
        }

        assignmentRepository.deleteById(id);
    }
}
