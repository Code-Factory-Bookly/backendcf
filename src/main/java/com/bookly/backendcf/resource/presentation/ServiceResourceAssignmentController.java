
package com.bookly.backendcf.resource.presentation;

import com.bookly.backendcf.resource.application.ServiceResourceAssignmentService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-11: Administración de asociaciones entre servicios y recursos físicos.
 */
@RestController
@RequestMapping("/api/v1/recursos/asignaciones")
public class ServiceResourceAssignmentController {

    private final ServiceResourceAssignmentService service;

    public ServiceResourceAssignmentController(
            ServiceResourceAssignmentService service) {
        this.service = service;
    }

    @PostMapping("/servicios/{serviceId}/recursos/{resourceId}")
    public ResponseEntity<Void> assign(
            @PathVariable UUID serviceId,
            @PathVariable UUID resourceId) {

        service.assign(serviceId, resourceId);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/servicios/{serviceId}")
    public ResponseEntity<List<UUID>> listResources(
            @PathVariable UUID serviceId) {

        return ResponseEntity.ok(service.listResources(serviceId));
    }

    @DeleteMapping("/servicios/{serviceId}/recursos/{resourceId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID serviceId,
            @PathVariable UUID resourceId) {

        service.remove(serviceId, resourceId);

        return ResponseEntity.noContent().build();
    }
}
