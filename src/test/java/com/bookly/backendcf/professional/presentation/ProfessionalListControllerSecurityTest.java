package com.bookly.backendcf.professional.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.professional.application.ProfessionalQueryService;
import com.bookly.backendcf.professional.presentation.dto.ProfessionalResponse;
import java.time.OffsetDateTime;
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
class ProfessionalListControllerSecurityTest {

    private static final String ENDPOINT = "/api/v1/profesionales";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private ProfessionalQueryService professionalQueryService;

    @BeforeEach
    void setUp() {
        when(professionalQueryService.list()).thenReturn(List.of(new ProfessionalResponse(
                UUID.randomUUID(), "sofia@example.com", "Sofia Prueba", "Ortodoncia",
                UserRole.PROFESSIONAL, OffsetDateTime.now())));
    }

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void clienteDevuelveForbidden() throws Exception {
        mockMvc.perform(get(ENDPOINT).header("Authorization", "Bearer " + tokenFor(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void administradorObtieneElListado() throws Exception {
        mockMvc.perform(get(ENDPOINT).header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Sofia Prueba"));
    }

    // El token solo lo acepta el filtro si la cuenta existe en la base (MantisBT BUG-005), asi que
    // cada token de prueba necesita una cuenta real persistida, no un objeto solo en memoria.
    private String tokenFor(UserRole role) {
        UserAccount account = new UserAccount(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
                "hash", "Usuario de prueba", role);
        return tokenService.createToken(accountRepository.save(account));
    }
}
