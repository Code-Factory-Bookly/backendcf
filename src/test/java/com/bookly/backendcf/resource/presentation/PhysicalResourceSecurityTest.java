
package com.bookly.backendcf.resource.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.configuration.SecurityConfiguration;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.resource.application.PhysicalResourceService;
import com.bookly.backendcf.resource.application.ServiceResourceAssignmentService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
        PhysicalResourceController.class,
        ServiceResourceAssignmentController.class
})
@EnableWebSecurity
@Import(SecurityConfiguration.class)
class PhysicalResourceSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PhysicalResourceService resourceService;

    @MockitoBean
    private ServiceResourceAssignmentService assignmentService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @Test
    void usuarioSinAutenticacionRecibe401() throws Exception {
        mockMvc.perform(get("/api/v1/recursos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteNoPuedeConsultarRecursos() throws Exception {
        mockMvc.perform(get("/api/v1/recursos")
                        .with(user("cliente").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void profesionalNoPuedeConsultarRecursos() throws Exception {
        mockMvc.perform(get("/api/v1/recursos")
                        .with(user("profesional").roles("PROFESSIONAL")))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorPuedeConsultarRecursos() throws Exception {
        when(resourceService.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/recursos")
                        .with(user("administrador").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void clienteNoPuedeCrearRecursos() throws Exception {
        mockMvc.perform(post("/api/v1/recursos")
                        .with(user("cliente").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cabina",
                                  "totalQuantity": 3
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteNoPuedeAsociarRecursos() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/api/v1/recursos/asignaciones/servicios/{serviceId}/recursos/{resourceId}",
                        serviceId,
                        resourceId)
                        .with(user("cliente").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorPuedeConsultarAsociaciones() throws Exception {
        UUID serviceId = UUID.randomUUID();

        when(assignmentService.listResources(serviceId))
                .thenReturn(List.of());

        mockMvc.perform(get(
                        "/api/v1/recursos/asignaciones/servicios/{serviceId}",
                        serviceId)
                        .with(user("administrador").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
