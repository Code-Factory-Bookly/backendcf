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
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.schedule.application.WeeklyScheduleService;
import com.bookly.backendcf.schedule.presentation.dto.ScheduleResponse;
import java.lang.reflect.Field;
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

    @MockitoBean
    private WeeklyScheduleService scheduleService;

    private final UUID ownId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
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
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CUSTOMER, ownId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void profesionalNoPuedeEditarHorarioAjeno() throws Exception {
        mockMvc.perform(put("/api/v1/horarios/" + otherId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.PROFESSIONAL, ownId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void profesionalPuedeEditarSuPropioHorario() throws Exception {
        mockMvc.perform(put("/api/v1/horarios/" + ownId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.PROFESSIONAL, ownId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalId").value(ownId.toString()));
    }

    private String tokenFor(UserRole role, UUID id) throws Exception {
        UserAccount account = new UserAccount("test@example.com", "hashed-password", "Usuario de prueba", role);
        Field idField = UserAccount.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(account, id);
        return tokenService.createToken(account);
    }
}
