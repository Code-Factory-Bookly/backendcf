package com.bookly.backendcf.auth.presentation;

import com.bookly.backendcf.auth.application.LoginService;
import com.bookly.backendcf.auth.application.MfaService;
import com.bookly.backendcf.auth.application.RegisterCustomerService;
import com.bookly.backendcf.auth.presentation.dto.LoginRequest;
import com.bookly.backendcf.auth.presentation.dto.LoginResponse;
import com.bookly.backendcf.auth.presentation.dto.MfaConfirmResponse;
import com.bookly.backendcf.auth.presentation.dto.MfaEnrollRequest;
import com.bookly.backendcf.auth.presentation.dto.MfaEnrollmentResponse;
import com.bookly.backendcf.auth.presentation.dto.MfaVerifyRequest;
import com.bookly.backendcf.auth.presentation.dto.RegisterCustomerRequest;
import com.bookly.backendcf.auth.presentation.dto.RegisterCustomerResponse;
import com.bookly.backendcf.auth.security.JwtTokenService;
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
    private final MfaService mfaService;
    private final JwtTokenService tokenService;

    public AuthController(RegisterCustomerService registerCustomerService, LoginService loginService,
                          MfaService mfaService, JwtTokenService tokenService) {
        this.registerCustomerService = registerCustomerService;
        this.loginService = loginService;
        this.mfaService = mfaService;
        this.tokenService = tokenService;
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

    @PostMapping("/mfa/enroll")
    public MfaEnrollmentResponse enroll(@Valid @RequestBody MfaEnrollRequest request) {
        MfaService.Enrollment enrollment = mfaService.startEnrollment(request.mfaToken());
        return new MfaEnrollmentResponse(enrollment.secret(), enrollment.otpauthUri());
    }

    @PostMapping("/mfa/enroll/confirm")
    public MfaConfirmResponse confirmEnroll(@Valid @RequestBody MfaVerifyRequest request) {
        MfaService.ConfirmedEnrollment confirmed = mfaService.confirmEnrollment(request.mfaToken(), request.code());
        return new MfaConfirmResponse(
                LoginResponse.authenticated(tokenService, confirmed.account()),
                confirmed.recoveryCodes());
    }

    @PostMapping("/mfa/verify")
    public LoginResponse verify(@Valid @RequestBody MfaVerifyRequest request) {
        return LoginResponse.authenticated(tokenService, mfaService.verify(request.mfaToken(), request.code()));
    }
}
