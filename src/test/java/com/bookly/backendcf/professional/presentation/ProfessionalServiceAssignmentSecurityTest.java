package com.bookly.backendcf.professional.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.professional.application.ProfessionalServiceAssignmentService;
import com.bookly.backendcf.professional.presentation.dto.ProfessionalSummary;
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
class ProfessionalServiceAssignmentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private ProfessionalServiceAssignmentService professionalServiceAssignmentService;

    private final UUID serviceId = UUID.randomUUID();
    private final UUID professionalId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(professionalServiceAssignmentService.listProfessionalsForService(eq(serviceId)))
                .thenReturn(List.of(new ProfessionalSummary(professionalId, "Pro Prueba", "Ortodoncia")));
        when(professionalServiceAssignmentService.listServiceIdsForProfessional(eq(professionalId)))
                .thenReturn(List.of(serviceId));
    }

    @Test
    void listarProfesionalesDeUnServicioEsPublico() throws Exception {
        mockMvc.perform(get("/api/v1/servicios/{id}/profesionales", serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Pro Prueba"));
    }

    @Test
    void consultarServiciosDeUnProfesionalSinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/profesionales/{id}/servicios", professionalId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void consultarServiciosDeUnProfesionalComoClienteDevuelveForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/profesionales/{id}/servicios", professionalId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorConsultaLosServiciosDeUnProfesional() throws Exception {
        mockMvc.perform(get("/api/v1/profesionales/{id}/servicios", professionalId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(serviceId.toString()));
    }

    @Test
    void reemplazarServiciosSinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/profesionales/{id}/servicios", professionalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administradorReemplazaLosServiciosDeUnProfesional() throws Exception {
        mockMvc.perform(put("/api/v1/profesionales/{id}/servicios", professionalId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceIds\":[\"" + serviceId + "\"]}"))
                .andExpect(status().isNoContent());
    }

    private String tokenFor(UserRole role) {
        UserAccount account = new UserAccount(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
                "hashed-password", "Usuario de prueba", role);
        return tokenService.createToken(accountRepository.save(account));
    }
}
