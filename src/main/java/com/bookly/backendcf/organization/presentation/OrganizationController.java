package com.bookly.backendcf.organization.presentation;

import com.bookly.backendcf.organization.application.RegisterOrganizationService;
import com.bookly.backendcf.organization.presentation.dto.RegisterOrganizationRequest;
import com.bookly.backendcf.organization.presentation.dto.RegisterOrganizationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final RegisterOrganizationService registerOrganizationService;

    public OrganizationController(RegisterOrganizationService registerOrganizationService) {
        this.registerOrganizationService = registerOrganizationService;
    }

    @PostMapping
    public ResponseEntity<RegisterOrganizationResponse> register(
            @Valid @RequestBody RegisterOrganizationRequest request) {
        RegisterOrganizationResponse response = registerOrganizationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
