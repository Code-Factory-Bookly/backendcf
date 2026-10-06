package com.bookly.backendcf.auth.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.application.RoleAssignmentService;
import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.presentation.dto.RoleAssignmentResponse;
import com.bookly.backendcf.auth.security.JwtTokenService;
import java.lang.reflect.Field;
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
class UserRoleControllerSecurityTest {

    private static final String VALID_REQUEST = "{\"role\":\"PROFESSIONAL\",\"specialty\":\"Ortodoncia\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @MockitoBean
    private RoleAssignmentService roleAssignmentService;

    private final UUID targetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(roleAssignmentService.assign(eq(targetId), any())).thenReturn(
                new RoleAssignmentResponse(targetId, "usuario@example.com", "Usuario", UserRole.PROFESSIONAL));
    }

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/" + targetId + "/rol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void clienteNoPuedeAsignarRoles() throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/" + targetId + "/rol")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void profesionalNoPuedeAsignarRoles() throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/" + targetId + "/rol")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.PROFESSIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorPuedeAsignarRoles() throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/" + targetId + "/rol")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROFESSIONAL"));
    }

    private String tokenFor(UserRole role) throws Exception {
        UserAccount account = new UserAccount("admin@example.com", "hashed-password", "Usuario de prueba", role);
        Field idField = UserAccount.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(account, UUID.randomUUID());
        return tokenService.createToken(account);
    }
}
