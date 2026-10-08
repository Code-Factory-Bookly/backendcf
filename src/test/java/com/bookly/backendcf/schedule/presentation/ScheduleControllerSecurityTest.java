package com.bookly.backendcf.schedule.presentation;

import static org.mockito.ArgumentMatchers.any;
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
import com.bookly.backendcf.schedule.application.WeeklyScheduleService;
import com.bookly.backendcf.schedule.presentation.dto.ScheduleResponse;
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
class ScheduleControllerSecurityTest {

    private static final String VALID_REQUEST = "{\"slots\":[{\"dayOfWeek\":\"MONDAY\","
            + "\"startTime\":\"08:00\",\"endTime\":\"12:00\"}]}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private WeeklyScheduleService scheduleService;

    private UserAccount ownAccount;
    private UUID ownId;
    private UUID otherId;

    @BeforeEach
    void setUp() {
        ownAccount = persistAccount(UserRole.PROFESSIONAL);
        ownId = ownAccount.getId();
        otherId = persistAccount(UserRole.PROFESSIONAL).getId();
        when(scheduleService.replace(eq(ownId), any()))
                .thenReturn(new ScheduleResponse(ownId, List.of()));
    }

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/horarios/" + ownId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void clienteDevuelveForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/horarios/" + ownId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void profesionalNoPuedeEditarHorarioAjeno() throws Exception {
        mockMvc.perform(put("/api/v1/horarios/" + otherId)
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void profesionalPuedeEditarSuPropioHorario() throws Exception {
        mockMvc.perform(put("/api/v1/horarios/" + ownId)
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalId").value(ownId.toString()));
    }

    // El token solo lo acepta el filtro si la cuenta existe en la base (MantisBT BUG-005), asi que
    // "propio" y "ajeno" ahora son cuentas reales persistidas, no un id inventado con reflexion.
    private UserAccount persistAccount(UserRole role) {
        UserAccount account = new UserAccount(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
                "hashed-password", "Usuario de prueba", role);
        return accountRepository.save(account);
    }

    private String tokenFor(UserRole role) {
        return tokenService.createToken(persistAccount(role));
    }
}
