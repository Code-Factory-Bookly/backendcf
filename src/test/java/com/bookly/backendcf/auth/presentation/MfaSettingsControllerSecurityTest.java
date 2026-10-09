package com.bookly.backendcf.auth.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.application.MfaService;
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
class MfaSettingsControllerSecurityTest {

    private static final String DISABLE_BODY = "{\"password\":\"Actual1!pass\",\"code\":\"123456\"}";
    private static final String REGENERATE_BODY = "{\"code\":\"123456\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private MfaService mfaService;

    private UserAccount admin;

    @BeforeEach
    void setUp() {
        admin = accountRepository.save(
                new UserAccount("admin-" + UUID.randomUUID() + "@example.com", "hash", "Admin", UserRole.ADMIN));
    }

    @Test
    void estadoSinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/mfa/recovery-codes/estado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void estadoClienteDevuelveForbidden() throws Exception {
        UserAccount customer = accountRepository.save(
                new UserAccount("cliente-" + UUID.randomUUID() + "@example.com", "hash", "Cliente", UserRole.CUSTOMER));

        mockMvc.perform(get("/api/v1/mfa/recovery-codes/estado")
                        .header("Authorization", "Bearer " + tokenService.createToken(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void estadoAdministradorDevuelveCodigosRestantes() throws Exception {
        when(mfaService.recoveryCodesRemaining(admin.getId())).thenReturn(7);

        mockMvc.perform(get("/api/v1/mfa/recovery-codes/estado")
                        .header("Authorization", "Bearer " + tokenService.createToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(7));
    }

    @Test
    void regenerarDevuelveDiezCodigosNuevos() throws Exception {
        List<String> codes = List.of(
                "AAAA-1111", "BBBB-2222", "CCCC-3333", "DDDD-4444", "EEEE-5555",
                "FFFF-6666", "GGGG-7777", "HHHH-8888", "IIII-9999", "JJJJ-0000");
        when(mfaService.regenerateRecoveryCodes(eq(admin.getId()), eq("123456"))).thenReturn(codes);

        mockMvc.perform(post("/api/v1/mfa/recovery-codes/regenerar")
                        .header("Authorization", "Bearer " + tokenService.createToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGENERATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10));
    }

    @Test
    void desactivarConCredencialesValidasDevuelveNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/mfa/desactivar")
                        .header("Authorization", "Bearer " + tokenService.createToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DISABLE_BODY))
                .andExpect(status().isNoContent());
    }

    @Test
    void desactivarSinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/mfa/desactivar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DISABLE_BODY))
                .andExpect(status().isUnauthorized());
    }
}
