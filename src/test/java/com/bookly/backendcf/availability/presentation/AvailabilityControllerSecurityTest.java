package com.bookly.backendcf.availability.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.availability.application.AvailabilityService;
import com.bookly.backendcf.availability.presentation.dto.AvailabilityResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AvailabilityControllerSecurityTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 12);
    private static final LocalDate TO = LocalDate.of(2026, 10, 18);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private AvailabilityService availabilityService;

    private UUID professionalId;
    private UUID serviceId;
    private String url;

    @BeforeEach
    void setUp() {
        professionalId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        url = "/api/v1/disponibilidad?profesionalId=" + professionalId + "&servicioId=" + serviceId
                + "&desde=2026-10-12&hasta=2026-10-18";
        when(availabilityService.find(eq(professionalId), eq(serviceId), any(), any()))
                .thenReturn(new AvailabilityResponse(
                        professionalId, serviceId, 60, FROM, TO, false, "sin horarios", List.of()));
    }

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void clienteAutenticadoConsultaLaDisponibilidad() throws Exception {
        mockMvc.perform(get(url).header("Authorization", "Bearer " + tokenFor(UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalId").value(professionalId.toString()))
                .andExpect(jsonPath("$.available").value(false));

        verify(availabilityService).find(professionalId, serviceId, FROM, TO);
    }

    @Test
    void fechaConFormatoInvalidoDevuelveBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/disponibilidad?profesionalId=" + professionalId
                        + "&servicioId=" + serviceId + "&desde=no-es-fecha&hasta=2026-10-18")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CUSTOMER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // El token solo lo acepta el filtro si la cuenta existe en la base (MantisBT BUG-005).
    private String tokenFor(UserRole role) {
        UserAccount account = accountRepository.save(new UserAccount(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
                "hashed-password", "Usuario de prueba", role));
        return tokenService.createToken(account);
    }
}