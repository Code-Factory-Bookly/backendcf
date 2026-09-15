package com.bookly.backendcf.organization.presentation;

import com.bookly.backendcf.organization.application.OrganizationProfileService;
import com.bookly.backendcf.organization.presentation.dto.OrganizationProfileRequest;
import com.bookly.backendcf.organization.presentation.dto.OrganizationProfileResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/profile")
public class OrganizationProfileController {

    private final OrganizationProfileService organizationProfileService;

    public OrganizationProfileController(OrganizationProfileService organizationProfileService) {
        this.organizationProfileService = organizationProfileService;
    }

    @GetMapping
    public ResponseEntity<OrganizationProfileResponse> get() {
        return ResponseEntity.ok(organizationProfileService.get());
    }

    @PutMapping
    public ResponseEntity<OrganizationProfileResponse> save(@Valid @RequestBody OrganizationProfileRequest request) {
        return ResponseEntity.ok(organizationProfileService.save(request));
    }
}
