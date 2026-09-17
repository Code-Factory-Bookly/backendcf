package com.bookly.backendcf.auth.presentation;

import com.bookly.backendcf.auth.application.RegisterCustomerService;
import com.bookly.backendcf.auth.application.LoginService;
import com.bookly.backendcf.auth.presentation.dto.LoginRequest;
import com.bookly.backendcf.auth.presentation.dto.LoginResponse;
import com.bookly.backendcf.auth.presentation.dto.RegisterCustomerRequest;
import com.bookly.backendcf.auth.presentation.dto.RegisterCustomerResponse;
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

    private final RegisterCustomerService registerCustomerService;
    private final LoginService loginService;

    public AuthController(RegisterCustomerService registerCustomerService, LoginService loginService) {
        this.registerCustomerService = registerCustomerService;
        this.loginService = loginService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterCustomerResponse> register(
            @Valid @RequestBody RegisterCustomerRequest request) {
        RegisterCustomerResponse response = registerCustomerService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request);
    }
}
