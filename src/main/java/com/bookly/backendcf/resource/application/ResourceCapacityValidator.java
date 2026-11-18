
package com.bookly.backendcf.resource.application;

import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.resource.domain.model.PhysicalResource;
import com.bookly.backendcf.resource.infrastructure.persistence.PhysicalResourceRepository;
import com.bookly.backendcf.resource.infrastructure.persistence.ServiceResourceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceCapacityValidator {

    private final PhysicalResourceRepository resourceRepository;
    private final ServiceResourceRepository assignmentRepository;
    private final BookingRepository bookingRepository;

    public ResourceCapacityValidator(
            PhysicalResourceRepository resourceRepository,
            ServiceResourceRepository assignmentRepository,
            BookingRepository bookingRepository
    ) {
        this.resourceRepository = resourceRepository;
        this.assignmentRepository = assignmentRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validate(
            UUID serviceId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        // Obtener los recursos requeridos por el servicio.
        // Ordenar los UUID para evitar bloqueos en distinto orden.
        List<UUID> resourceIds = assignmentRepository
                .findByServiceId(serviceId)
                .stream()
                .map(assignment -> assignment.getResourceId())
                .distinct()
                .sorted()
                .toList();

        // Sin recursos asociados no existe inventario que validar.
        if (resourceIds.isEmpty()) {
            return;
        }

        // Adquirir primero todos los bloqueos necesarios.
        // Se mantienen hasta finalizar la transaccion de reserva.
        List<PhysicalResource> resources = resourceIds.stream()
                .map(resourceId -> resourceRepository
                        .findByIdForUpdate(resourceId)
                        .orElseThrow(() ->
                                new PhysicalResourceNotFoundException(resourceId)))
                .toList();

        // Consultar ocupacion despues de obtener los bloqueos.
        for (PhysicalResource resource : resources) {

            long occupied = bookingRepository
                    .countConfirmedOverlappingByResource(
                            resource.getId(),
                            startTime,
                            endTime
                    );

            if (occupied >= resource.getTotalQuantity()) {
                throw new BookingConflictException(
                        "RESOURCE_UNAVAILABLE",
                        "No hay recursos fisicos disponibles para el horario seleccionado"
                );
            }
        }
    }
}
