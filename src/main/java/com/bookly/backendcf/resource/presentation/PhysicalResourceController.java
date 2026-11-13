
package com.bookly.backendcf.resource.presentation;

import com.bookly.backendcf.resource.application.PhysicalResourceService;
import com.bookly.backendcf.resource.domain.model.PhysicalResource;
import com.bookly.backendcf.resource.presentation.dto.PhysicalResourceRequest;
import com.bookly.backendcf.resource.presentation.dto.PhysicalResourceResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recursos")
public class PhysicalResourceController {

    private final PhysicalResourceService service;

    public PhysicalResourceController(PhysicalResourceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PhysicalResourceResponse>> list() {
        List<PhysicalResourceResponse> resources = service.list()
                .stream()
                .map(PhysicalResourceResponse::from)
                .toList();

        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PhysicalResourceResponse> get(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                PhysicalResourceResponse.from(service.get(id))
        );
    }

    @PostMapping
    public ResponseEntity<PhysicalResourceResponse> create(
            @Valid @RequestBody PhysicalResourceRequest request) {

        PhysicalResource resource = service.create(
                request.name(),
                request.totalQuantity()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PhysicalResourceResponse.from(resource));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PhysicalResourceResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PhysicalResourceRequest request) {

        PhysicalResource resource = service.update(
                id,
                request.name(),
                request.totalQuantity()
        );

        return ResponseEntity.ok(
                PhysicalResourceResponse.from(resource)
        );
    }
}
