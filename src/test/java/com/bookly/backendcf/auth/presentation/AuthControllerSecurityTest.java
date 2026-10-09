package com.bookly.backendcf.auth.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.application.MfaService;
import com.bookly.backendcf.auth.application.PasswordResetService;
import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private MfaService mfaService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    private UserAccount admin;

    @BeforeEach
    void setUp() {
        admin = accountRepository.save(
                new UserAccount("admin-" + UUID.randomUUID() + "@example.com", "hash", "Admin", UserRole.ADMIN));
    }

    @Test
    void enrollDevuelveElSecretoYLaUriOtpauth() throws Exception {
        when(mfaService.startEnrollment("mfa-token"))
                .thenReturn(new MfaService.Enrollment("SECRET123", "otpauth://totp/Bookly:admin"));

        mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"mfa-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("SECRET123"))
                .andExpect(jsonPath("$.otpauthUri").value("otpauth://totp/Bookly:admin"));
    }

    @Test
    void confirmEnrollDevuelveSesionYCodigosDeRecuperacion() throws Exception {
        when(mfaService.confirmEnrollment(eq("mfa-token"), eq("123456"), eq(true)))
                .thenReturn(new MfaService.ConfirmedEnrollment(admin, List.of("AAAA-1111", "BBBB-2222"), "device-token"));

        mockMvc.perform(post("/api/v1/auth/mfa/enroll/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"mfa-token\",\"code\":\"123456\",\"trustDevice\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.session.deviceToken").value("device-token"))
                .andExpect(jsonPath("$.recoveryCodes.length()").value(2));
    }

    @Test
    void verifyDevuelveUnaSesionAutenticada() throws Exception {
        when(mfaService.verify(eq("mfa-token"), eq("654321"), eq(false)))
                .thenReturn(new MfaService.VerifiedSession(admin, null));

        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"mfa-token\",\"code\":\"654321\",\"trustDevice\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.mfaRequired").value(false));
    }

    @Test
    void skipDevuelveUnaSesionSinExigirCodigo() throws Exception {
        when(mfaService.skipEnrollment("mfa-token")).thenReturn(admin);

        mockMvc.perform(post("/api/v1/auth/mfa/skip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"mfa-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.mfaSetupRequired").value(false));
    }

    @Test
    void verifyConCodigoIncorrectoDevuelveUnauthorized() throws Exception {
        when(mfaService.verify(eq("mfa-token"), eq("000000"), eq(false)))
                .thenThrow(new com.bookly.backendcf.auth.application.InvalidMfaCodeException());

        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"mfa-token\",\"code\":\"000000\",\"trustDevice\":false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MFA_CODE"));
    }

    @Test
    void skipConMfaYaActivoDevuelveUnauthorized() throws Exception {
        when(mfaService.skipEnrollment("mfa-token"))
                .thenThrow(new com.bookly.backendcf.auth.application.InvalidMfaTokenException());

        mockMvc.perform(post("/api/v1/auth/mfa/skip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"mfa-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MFA_TOKEN"));
    }

    @Test
    void cambiarClaveSinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Actual1!\",\"newPassword\":\"Nueva1!pass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarClaveAutenticadoDevuelveNoContent() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + tokenService.createToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Actual1!\",\"newPassword\":\"Nueva1!pass\"}"))
                .andExpect(status().isNoContent());
    }
}
