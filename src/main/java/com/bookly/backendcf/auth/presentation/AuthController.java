package com.bookly.backendcf.auth.presentation;

import com.bookly.backendcf.auth.application.RegisterPatientService;
import com.bookly.backendcf.auth.presentation.dto.RegisterPatientRequest;
import com.bookly.backendcf.auth.presentation.dto.RegisterPatientResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterPatientService registerPatientService;

    public AuthController(RegisterPatientService registerPatientService) {
        this.registerPatientService = registerPatientService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterPatientResponse> register(
            @Valid @RequestBody RegisterPatientRequest request) {
        RegisterPatientResponse response = registerPatientService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
