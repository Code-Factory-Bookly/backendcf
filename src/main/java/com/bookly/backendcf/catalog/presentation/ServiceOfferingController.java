package com.bookly.backendcf.catalog.presentation;

import com.bookly.backendcf.catalog.application.ServiceOfferingService;
import com.bookly.backendcf.catalog.presentation.dto.ServiceOfferingRequest;
import com.bookly.backendcf.catalog.presentation.dto.ServiceOfferingResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/servicios")
public class ServiceOfferingController {

    private final ServiceOfferingService serviceOfferingService;

    public ServiceOfferingController(ServiceOfferingService serviceOfferingService) {
        this.serviceOfferingService = serviceOfferingService;
    }

    @GetMapping
    public ResponseEntity<List<ServiceOfferingResponse>> list(
            @RequestParam(name = "categoria", required = false) String category) {
        return ResponseEntity.ok(serviceOfferingService.list(category));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceOfferingResponse> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(serviceOfferingService.get(id));
    }

    @PostMapping
    public ResponseEntity<ServiceOfferingResponse> create(@Valid @RequestBody ServiceOfferingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceOfferingService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceOfferingResponse> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ServiceOfferingRequest request) {
        return ResponseEntity.ok(serviceOfferingService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        serviceOfferingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
