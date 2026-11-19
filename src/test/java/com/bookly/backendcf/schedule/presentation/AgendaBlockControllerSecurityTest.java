package com.bookly.backendcf.schedule.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.schedule.application.AgendaBlockConflictException;
import com.bookly.backendcf.schedule.application.AgendaBlockNotFoundException;
import com.bookly.backendcf.schedule.application.AgendaBlockService;
import com.bookly.backendcf.schedule.application.ScheduleValidationException;
import com.bookly.backendcf.schedule.presentation.dto.AgendaBlockResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HU-05: seguridad y contrato HTTP de los bloqueos de agenda.
 *
 * <p>Referencia a los criterios de aceptacion (Gherkin, HU-05 refinada Sprint 2):
 * <ul>
 *   <li>Escenario "Bloquear una franja sin reservas previas".</li>
 *   <li>Escenario "Intento de bloquear rango con reservas confirmadas activas".</li>
 * </ul>
 *
 * <p>Reglas y errores HTTP: {@code docs/HU-05-bloqueo-agenda.md}.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class AgendaBlockControllerSecurityTest {

    /**
     * Rango relativo al momento de ejecucion: el servicio valida contra LocalDateTime.now(),
     * asi que una fecha fija acabaria expirando la suite.
     */
    private LocalDateTime inicio;
    private LocalDateTime fin;
    private String validRequest;
    private String inicioIso;
    private String finIso;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private AgendaBlockService blockService;

    @MockitoBean
    private ProfessionalRepository professionalRepository;

    private UserAccount ownAccount;
    private UUID ownId;
    private UUID otherId;

    @BeforeEach
    void setUp() {
        inicio = LocalDateTime.now().plusDays(30).withHour(8).withMinute(0).withSecond(0).withNano(0);
        fin = inicio.plusHours(4);
        // Jackson serializa el LocalDateTime con segundos explicitos (por ejemplo
        // "2030-05-01T08:00:00"), que no es lo mismo que LocalDateTime.toString() cuando
        // los segundos son cero.
        DateTimeFormatter iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        inicioIso = inicio.format(iso);
        finIso = fin.format(iso);
        validRequest = "{\"startTime\":\"" + inicioIso + "\",\"endTime\":\"" + finIso + "\","
                + "\"reason\":\"Vacaciones\"}";
        ownAccount = persistAccount(UserRole.PROFESSIONAL);
        ownId = ownAccount.getId();
        otherId = persistAccount(UserRole.PROFESSIONAL).getId();
        when(professionalRepository.existsById(ownId)).thenReturn(true);
        when(professionalRepository.existsById(otherId)).thenReturn(true);
        when(blockService.create(eq(ownId), any()))
                .thenReturn(response(ownId));
    }

    // ------------------------------------------------------------------
    // QA/Exception: sin token, la API responde 401.
    // ------------------------------------------------------------------
    @Test
    void sinTokenDevuelveUnauthorized() throws Exception {
        // Act
        mockMvc.perform(get("/api/v1/horarios/" + ownId + "/bloqueos"))
                // Assert
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    // ------------------------------------------------------------------
    // QA/Exception: sin token, crear un bloqueo responde 401.
    // ------------------------------------------------------------------
    @Test
    void sinTokenNoPuedeCrearUnBloqueo() throws Exception {
        // Act
        mockMvc.perform(post("/api/v1/horarios/" + ownId + "/bloqueos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                // Assert
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(blockService, never()).create(eq(ownId), any());
    }

    // ------------------------------------------------------------------
    // QA/Exception: sin token, borrar un bloqueo responde 401.
    // ------------------------------------------------------------------
    @Test
    void sinTokenNoPuedeBorrarUnBloqueo() throws Exception {
        // Arrange
        UUID blockId = UUID.randomUUID();

        // Act
        mockMvc.perform(delete("/api/v1/horarios/" + ownId + "/bloqueos/" + blockId))
                // Assert
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(blockService, never()).delete(eq(ownId), eq(blockId));
    }

    // ------------------------------------------------------------------
    // QA/Exception: el rol CUSTOMER no puede gestionar bloqueos.
    // ------------------------------------------------------------------
    @Test
    void clienteDevuelveForbidden() throws Exception {
        // Act
        mockMvc.perform(post("/api/v1/horarios/" + ownId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                // Assert
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ------------------------------------------------------------------
    // QA/Exception: un profesional no puede listar los bloqueos de otro.
    // ------------------------------------------------------------------
    @Test
    void profesionalNoPuedeListarLosBloqueosDeOtro() throws Exception {
        // Act
        mockMvc.perform(get("/api/v1/horarios/" + otherId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount)))
                // Assert
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ------------------------------------------------------------------
    // QA/Exception: un profesional no puede bloquear la agenda de otro.
    // ------------------------------------------------------------------
    @Test
    void profesionalNoPuedeBloquearLaAgendaDeOtro() throws Exception {
        // Act
        mockMvc.perform(post("/api/v1/horarios/" + otherId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                // Assert
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ------------------------------------------------------------------
    // QA/Exception: un profesional no puede borrar el bloqueo de otro.
    // ------------------------------------------------------------------
    @Test
    void profesionalNoPuedeBorrarElBloqueoDeOtro() throws Exception {
        // Act
        mockMvc.perform(delete("/api/v1/horarios/" + otherId + "/bloqueos/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount)))
                // Assert
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // El profesional lista sus propios bloqueos.
    // ------------------------------------------------------------------
    @Test
    void profesionalPuedeListarSusPropiosBloqueos() throws Exception {
        // Arrange
        when(blockService.findUpcoming(ownId)).thenReturn(List.of(response(ownId)));

        // Act
        mockMvc.perform(get("/api/v1/horarios/" + ownId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount)))
                // Assert
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].professionalId").value(ownId.toString()));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // El profesional crea un bloqueo en su propia agenda.
    // ------------------------------------------------------------------
    @Test
    void profesionalPuedeCrearSuPropioBloqueo() throws Exception {
        // Act
        mockMvc.perform(post("/api/v1/horarios/" + ownId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                // Assert
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.professionalId").value(ownId.toString()))
                .andExpect(jsonPath("$.startTime").value(inicioIso))
                .andExpect(jsonPath("$.endTime").value(finIso))
                .andExpect(jsonPath("$.reason").value("Vacaciones"));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // El profesional borra uno de sus bloqueos.
    // ------------------------------------------------------------------
    @Test
    void profesionalPuedeBorrarSuPropioBloqueo() throws Exception {
        // Arrange
        UUID blockId = UUID.randomUUID();

        // Act
        mockMvc.perform(delete("/api/v1/horarios/" + ownId + "/bloqueos/" + blockId)
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount)))
                // Assert
                .andExpect(status().isNoContent());
        verify(blockService).delete(ownId, blockId);
    }

    // ------------------------------------------------------------------
    // QA/Exception: un profesional inexistente responde 404.
    // ------------------------------------------------------------------
    @Test
    void profesionalInexistenteDevuelveNotFound() throws Exception {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(professionalRepository.existsById(unknownId)).thenReturn(false);

        // Act
        mockMvc.perform(get("/api/v1/horarios/" + unknownId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount)))
                // Assert
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PROFESSIONAL_NOT_FOUND"));
    }

    // ------------------------------------------------------------------
    // QA/Exception: el motivo de mas de 200 caracteres responde 400.
    // ------------------------------------------------------------------
    @Test
    void motivoDemasiadoLargoDevuelveBadRequest() throws Exception {
        // Arrange
        String motivoLargo = "a".repeat(201);

        // Act
        mockMvc.perform(post("/api/v1/horarios/" + ownId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + inicioIso + "\",\"endTime\":\"" + finIso + "\","
                                + "\"reason\":\"" + motivoLargo + "\"}"))
                // Assert
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(blockService, never()).create(eq(ownId), any());
    }

    // ------------------------------------------------------------------
    // QA/Exception: el administrador puede gestionar los bloqueos de otro profesional.
    // ------------------------------------------------------------------
    @Test
    void administradorPuedeGestionarLosBloqueosDeOtroProfesional() throws Exception {
        // Arrange
        UUID adminId = persistAccount(UserRole.ADMIN).getId();
        when(blockService.findUpcoming(otherId)).thenReturn(List.of(response(otherId)));

        // Act
        mockMvc.perform(get("/api/v1/horarios/" + otherId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenService.createToken(
                                accountRepository.findById(adminId).orElseThrow())))
                // Assert
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].professionalId").value(otherId.toString()));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Intento de bloquear rango con reservas confirmadas activas.
    // El conflicto con reservas confirmadas responde 409 con el detalle.
    // ------------------------------------------------------------------
    @Test
    void elConflictoConReservasConfirmadasDevuelveConflict() throws Exception {
        // Arrange
        when(blockService.create(eq(ownId), any()))
                .thenThrow(AgendaBlockConflictException.bookingsInRange(
                        Map.of("reserva-1", "21/12 09:00–10:00 · Ana · Consulta")));

        // Act
        mockMvc.perform(post("/api/v1/horarios/" + ownId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                // Assert
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AGENDA_BLOCK_HAS_BOOKINGS"))
                .andExpect(jsonPath("$.details['reserva-1']").exists());
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Intento de bloquear rango con reservas confirmadas activas.
    // Con 3 reservas confirmadas, el detalle trae una entrada por cita.
    // ------------------------------------------------------------------
    @Test
    void elConflictoConTresReservasConfirmadasDetallaCadaCita() throws Exception {
        // Arrange
        when(blockService.create(eq(ownId), any()))
                .thenThrow(AgendaBlockConflictException.bookingsInRange(Map.of(
                        "reserva-1", "21/12 09:00–10:00 · Ana · Consulta",
                        "reserva-2", "21/12 11:00–12:00 · Luis · Terapia",
                        "reserva-3", "21/12 14:00–15:00 · Sara · Asesoría")));

        // Act
        mockMvc.perform(post("/api/v1/horarios/" + ownId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                // Assert
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AGENDA_BLOCK_HAS_BOOKINGS"))
                .andExpect(jsonPath("$.details['reserva-1']").exists())
                .andExpect(jsonPath("$.details['reserva-2']").exists())
                .andExpect(jsonPath("$.details['reserva-3']").exists());
    }

    // ------------------------------------------------------------------
    // QA/Exception: el solapamiento con otro bloqueo responde 409.
    // ------------------------------------------------------------------
    @Test
    void elSolapamientoConOtroBloqueoDevuelveConflict() throws Exception {
        // Arrange
        when(blockService.create(eq(ownId), any()))
                .thenThrow(AgendaBlockConflictException.overlapsBlock(
                        Map.of("bloqueo-1", "21/12 08:00–12:00")));

        // Act
        mockMvc.perform(post("/api/v1/horarios/" + ownId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                // Assert
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AGENDA_BLOCK_OVERLAP"));
    }

    // ------------------------------------------------------------------
    // QA/Exception: el rango invertido responde 400 con el detalle de endTime.
    // ------------------------------------------------------------------
    @Test
    void elRangoInvertidoDevuelveBadRequest() throws Exception {
        // Arrange
        when(blockService.create(eq(ownId), any()))
                .thenThrow(new ScheduleValidationException(
                        Map.of("endTime", "Debe ser posterior al inicio del bloqueo")));

        // Act
        mockMvc.perform(post("/api/v1/horarios/" + ownId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                // Assert
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SCHEDULE_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.endTime").exists());
    }

    // ------------------------------------------------------------------
    // QA/Exception: borrar un bloqueo inexistente responde 404.
    // ------------------------------------------------------------------
    @Test
    void borrarUnBloqueoInexistenteDevuelveNotFound() throws Exception {
        // Arrange
        UUID blockId = UUID.randomUUID();
        doThrow(new AgendaBlockNotFoundException(blockId))
                .when(blockService).delete(ownId, blockId);

        // Act
        mockMvc.perform(delete("/api/v1/horarios/" + ownId + "/bloqueos/" + blockId)
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount)))
                // Assert
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("AGENDA_BLOCK_NOT_FOUND"));
    }

    // ------------------------------------------------------------------
    // QA/Exception: falta startTime o endTime responde 400 de validacion.
    // ------------------------------------------------------------------
    @Test
    void faltanLasFechasObligatoriasDevuelveBadRequest() throws Exception {
        // Act
        mockMvc.perform(post("/api/v1/horarios/" + ownId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + inicioIso + "\"}"))
                // Assert
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(blockService, never()).create(eq(ownId), any());
    }

    // ------------------------------------------------------------------
    // QA/Exception: el cuerpo ilegible responde 400.
    // ------------------------------------------------------------------
    @Test
    void cuerpoIlegibleDevuelveBadRequest() throws Exception {
        // Act
        mockMvc.perform(post("/api/v1/horarios/" + ownId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenService.createToken(ownAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":"))
                // Assert
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(blockService, never()).create(eq(ownId), any());
    }

    // El token solo lo acepta el filtro si la cuenta existe en la base (MantisBT BUG-005), asi que
    // "propio" y "ajeno" son cuentas reales persistidas, no un id inventado con reflexion.
    private UserAccount persistAccount(UserRole role) {
        UserAccount account = new UserAccount(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
                "hashed-password", "Usuario de prueba", role);
        return accountRepository.save(account);
    }

    private String tokenFor(UserRole role) {
        return tokenService.createToken(persistAccount(role));
    }

    private AgendaBlockResponse response(UUID professionalId) {
        return new AgendaBlockResponse(
                UUID.randomUUID(), professionalId, inicio, fin, "Vacaciones", OffsetDateTime.now());
    }
}
