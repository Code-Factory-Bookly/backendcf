package com.bookly.backendcf.professional.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.professional.application.RegisterProfessionalService;
import com.bookly.backendcf.professional.presentation.dto.ProfessionalResponse;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
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
class ProfessionalControllerSecurityTest {

    private static final String PROFESSIONAL_ENDPOINT = "/api/v1/profesionales";
    private static final String VALID_REQUEST = "{\"email\":\"sofia@example.com\","
            + "\"password\":\"Valid1!pass\",\"fullName\":\"Sofia Prueba\","
            + "\"specialty\":\"Ortodoncia\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @MockitoBean
    private RegisterProfessionalService registerProfessionalService;

    @BeforeEach
    void setUp() {
        UUID professionalId = UUID.randomUUID();
        when(registerProfessionalService.register(any())).thenReturn(new ProfessionalResponse(
                professionalId,
                "sofia@example.com",
                "Sofia Prueba",
                "Ortodoncia",
                UserRole.PROFESSIONAL,
                OffsetDateTime.now()));
    }

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(post(PROFESSIONAL_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void usuarioNoAdministradorDevuelveForbidden() throws Exception {
        String customerToken = tokenFor(UserRole.CUSTOMER);

        mockMvc.perform(post(PROFESSIONAL_ENDPOINT)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void administradorPuedeRegistrarProfesional() throws Exception {
        String adminToken = tokenFor(UserRole.ADMIN);

        mockMvc.perform(post(PROFESSIONAL_ENDPOINT)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PROFESSIONAL"))
                .andExpect(jsonPath("$.specialty").value("Ortodoncia"));
    }

    private String tokenFor(UserRole role) throws Exception {
        UserAccount account = new UserAccount(
                "test@example.com",
                "hashed-password",
                "Usuario de prueba",
                role);
        Field idField = UserAccount.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(account, UUID.randomUUID());
        return tokenService.createToken(account);
    }
}
