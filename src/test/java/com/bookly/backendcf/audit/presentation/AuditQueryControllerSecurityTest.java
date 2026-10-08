package com.bookly.backendcf.audit.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.audit.application.AuditQueryService;
import com.bookly.backendcf.audit.presentation.dto.AuditLogPageResponse;
import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
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
class AuditQueryControllerSecurityTest {

    private static final String ENDPOINT = "/api/v1/auditoria";
    private static final String FROM = "2026-10-01T00:00:00Z";
    private static final String TO = "2026-10-08T23:59:59Z";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private AuditQueryService auditQueryService;

    @BeforeEach
    void setUp() {
        when(auditQueryService.find(any(OffsetDateTime.class), any(OffsetDateTime.class), eq(0), eq(50)))
                .thenReturn(new AuditLogPageResponse(List.of(), 0, 50, 0, 0));
    }

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("from", FROM).param("to", TO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void clienteDevuelveForbidden() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM)
                        .param("to", TO)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void profesionalDevuelveForbidden() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM)
                        .param("to", TO)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.PROFESSIONAL)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorPuedeConsultarLaBitacora() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM)
                        .param("to", TO)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50));
    }

    private String tokenFor(UserRole role) {
        UserAccount account = new UserAccount(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
                "hashed-password", "Usuario de prueba", role);
        return tokenService.createToken(accountRepository.save(account));
    }
}
