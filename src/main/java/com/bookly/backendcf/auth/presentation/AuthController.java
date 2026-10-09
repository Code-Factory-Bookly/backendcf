package com.bookly.backendcf.auth.presentation;

import com.bookly.backendcf.auth.application.LoginService;
import com.bookly.backendcf.auth.application.MfaService;
import com.bookly.backendcf.auth.application.PasswordResetService;
import com.bookly.backendcf.auth.application.RegisterCustomerService;
import com.bookly.backendcf.auth.presentation.dto.ChangePasswordRequest;
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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final PasswordResetService passwordResetService;

    public AuthController(RegisterCustomerService registerCustomerService, LoginService loginService,
                          MfaService mfaService, JwtTokenService tokenService,
                          PasswordResetService passwordResetService) {
        this.registerCustomerService = registerCustomerService;
        this.loginService = loginService;
        this.mfaService = mfaService;
        this.tokenService = tokenService;
        this.passwordResetService = passwordResetService;
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
        MfaService.ConfirmedEnrollment confirmed =
                mfaService.confirmEnrollment(request.mfaToken(), request.code(), request.trustDevice());
        return new MfaConfirmResponse(
                LoginResponse.authenticated(tokenService, confirmed.account(), confirmed.deviceToken()),
                confirmed.recoveryCodes());
    }

    @PostMapping("/mfa/verify")
    public LoginResponse verify(@Valid @RequestBody MfaVerifyRequest request) {
        MfaService.VerifiedSession session =
                mfaService.verify(request.mfaToken(), request.code(), request.trustDevice());
        return LoginResponse.authenticated(tokenService, session.account(), session.deviceToken());
    }

    // Omitir la configuracion del segundo factor por esta vez. No marca mfaEnabled, asi que el
    // siguiente login vuelve a pedir la configuracion (ADR-010).
    @PostMapping("/mfa/skip")
    public LoginResponse skip(@Valid @RequestBody MfaEnrollRequest request) {
        return LoginResponse.authenticated(tokenService, mfaService.skipEnrollment(request.mfaToken()));
    }

    // Parche sin HU-10: no hay recuperacion de clave por correo. Esto permite al propio usuario
    // cambiarla una vez adentro (por ejemplo, tras recibir una clave temporal de un ADMIN).
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        passwordResetService.changeOwnPassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
