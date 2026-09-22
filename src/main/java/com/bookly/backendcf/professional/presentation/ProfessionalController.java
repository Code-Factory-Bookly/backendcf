package com.bookly.backendcf.professional.presentation;

import com.bookly.backendcf.professional.application.RegisterProfessionalService;
import com.bookly.backendcf.professional.presentation.dto.ProfessionalRequest;
import com.bookly.backendcf.professional.presentation.dto.ProfessionalResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profesionales")
public class ProfessionalController {

    private final RegisterProfessionalService registerProfessionalService;

    public ProfessionalController(RegisterProfessionalService registerProfessionalService) {
        this.registerProfessionalService = registerProfessionalService;
    }

    @PostMapping
    public ResponseEntity<ProfessionalResponse> create(@Valid @RequestBody ProfessionalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registerProfessionalService.register(request));
    }
}