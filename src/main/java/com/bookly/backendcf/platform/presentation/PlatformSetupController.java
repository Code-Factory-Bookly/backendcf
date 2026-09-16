package com.bookly.backendcf.platform.presentation;

import com.bookly.backendcf.platform.application.PlatformSetupService;
import com.bookly.backendcf.platform.presentation.dto.PlatformSetupRequest;
import com.bookly.backendcf.platform.presentation.dto.PlatformSetupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformSetupController {

    private final PlatformSetupService platformSetupService;

    public PlatformSetupController(PlatformSetupService platformSetupService) {
        this.platformSetupService = platformSetupService;
    }

    @PostMapping("/setup")
    public ResponseEntity<PlatformSetupResponse> setup(@Valid @RequestBody PlatformSetupRequest request) {
        PlatformSetupResponse response = platformSetupService.setup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
