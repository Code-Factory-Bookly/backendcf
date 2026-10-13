package com.bookly.backendcf.professional.presentation;

import com.bookly.backendcf.professional.application.ProfessionalQueryService;
import com.bookly.backendcf.professional.application.ProfessionalServiceAssignmentService;
import com.bookly.backendcf.professional.application.RegisterProfessionalService;
import com.bookly.backendcf.professional.presentation.dto.ProfessionalRequest;
import com.bookly.backendcf.professional.presentation.dto.ProfessionalResponse;
import com.bookly.backendcf.professional.presentation.dto.ServiceIdsRequest;
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
@RequestMapping("/api/v1/profesionales")
public class ProfessionalController {

    private final RegisterProfessionalService registerProfessionalService;
    private final ProfessionalQueryService professionalQueryService;
    private final ProfessionalServiceAssignmentService professionalServiceAssignmentService;

    public ProfessionalController(
            RegisterProfessionalService registerProfessionalService,
            ProfessionalQueryService professionalQueryService,
            ProfessionalServiceAssignmentService professionalServiceAssignmentService) {
        this.registerProfessionalService = registerProfessionalService;
        this.professionalQueryService = professionalQueryService;
        this.professionalServiceAssignmentService = professionalServiceAssignmentService;
    }

    @GetMapping
    public List<ProfessionalResponse> list() {
        return professionalQueryService.list();
    }

    @PostMapping
    public ResponseEntity<ProfessionalResponse> create(@Valid @RequestBody ProfessionalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registerProfessionalService.register(request));
    }

    @GetMapping("/{id}/servicios")
    public List<UUID> services(@PathVariable UUID id) {
        return professionalServiceAssignmentService.listServiceIdsForProfessional(id);
    }

    @PutMapping("/{id}/servicios")
    public ResponseEntity<Void> replaceServices(@PathVariable UUID id, @Valid @RequestBody ServiceIdsRequest request) {
        professionalServiceAssignmentService.replaceServicesForProfessional(id, request.serviceIds());
        return ResponseEntity.noContent().build();
    }
}
