package com.bookly.backendcf.booking.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.booking.application.ProfessionalAgendaService;
import com.bookly.backendcf.booking.presentation.dto.ProfessionalAgendaResponse;
import com.bookly.backendcf.professional.domain.model.Professional;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pruebas del endpoint "Mi Agenda" del profesional (HU-14).
 *
 * <p>La ruta {@code /api/v1/reservas/profesional/agenda} no recibe el identificador del
 * profesional: se resuelve desde el token JWT. Por eso un profesional no puede pedir la agenda
 * de otro.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class ProfessionalAgendaControllerTest {

    private static final String ENDPOINT = "/api/v1/reservas/profesional/agenda";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @MockitoBean
    private ProfessionalAgendaService agendaService;

    // ================= Escenario 1: agenda del dia =================

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda de atencion diaria
    void devuelveLaAgendaDelProfesionalAutenticado() throws Exception {
        // Arrange
        UserAccount professional = createAccount(UserRole.PROFESSIONAL);
        String token = tokenService.createToken(professional);
        LocalDate today = LocalDate.of(2026, 11, 22);
        when(agendaService.find(eq(professional.getId()), any(), any()))
                .thenReturn(new ProfessionalAgendaResponse(today, today, List.of(
                        item(LocalDateTime.of(2026, 11, 22, 9, 0), "Ana Cliente", "Consulta"),
                        item(LocalDateTime.of(2026, 11, 22, 10, 0), "Beto Cliente", "Control")),
                        null));

        // Act
        mockMvc.perform(get(ENDPOINT).header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].startTime").value("2026-11-22T09:00:00"))
                .andExpect(jsonPath("$.content[0].customerName").value("Ana Cliente"))
                .andExpect(jsonPath("$.content[0].serviceName").value("Consulta"))
                .andExpect(jsonPath("$.content[1].customerName").value("Beto Cliente"))
                .andExpect(jsonPath("$.from").value("2026-11-22"))
                .andExpect(jsonPath("$.to").value("2026-11-22"));

        verify(agendaService).find(professional.getId(), null, null);
    }

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda de atencion diaria
    // El identificador sale del token: un "professionalId" en la URL se ignora.
    void ignoraUnIdentificadorDeProfesionalEnLaPeticion() throws Exception {
        // Arrange
        UserAccount professional = createAccount(UserRole.PROFESSIONAL);
        String token = tokenService.createToken(professional);
        UUID otherProfessionalId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 11, 22);
        when(agendaService.find(eq(professional.getId()), any(), any()))
                .thenReturn(new ProfessionalAgendaResponse(today, today, List.of(), "libre"));

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .param("professionalId", otherProfessionalId.toString())
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isOk());

        verify(agendaService).find(professional.getId(), null, null);
    }

    // ================= Escenario 2: rango de fechas =================

    @Test
    // Escenario Gherkin HU-14: Consultar agenda en un rango de fechas ampliado
    void pasaElRangoDeFechasOpcionalAlServicio() throws Exception {
        // Arrange
        UserAccount professional = createAccount(UserRole.PROFESSIONAL);
        String token = tokenService.createToken(professional);
        LocalDate monday = LocalDate.of(2026, 11, 30);
        LocalDate friday = LocalDate.of(2026, 12, 4);
        when(agendaService.find(professional.getId(), monday, friday))
                .thenReturn(new ProfessionalAgendaResponse(monday, friday, List.of(
                        item(LocalDateTime.of(2026, 11, 30, 9, 0), "Ana Cliente", "Consulta")),
                        null));

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .param("from", "2026-11-30")
                        .param("to", "2026-12-04")
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-30"))
                .andExpect(jsonPath("$.to").value("2026-12-04"))
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(agendaService).find(professional.getId(), monday, friday);
    }

    @Test
    // Escenario Gherkin HU-14: Consultar agenda en un rango de fechas ampliado
    void rechazaUnRangoInvalidoConValidationError() throws Exception {
        // Arrange
        UserAccount professional = createAccount(UserRole.PROFESSIONAL);
        String token = tokenService.createToken(professional);
        when(agendaService.find(eq(professional.getId()), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La fecha inicial no puede ser posterior a la fecha final"));

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .param("from", "2026-12-04")
                        .param("to", "2026-11-30")
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    // Escenario Gherkin HU-14: Consultar agenda en un rango de fechas ampliado
    void rechazaUnaFechaConFormatoInvalido() throws Exception {
        // Arrange
        UserAccount professional = createAccount(UserRole.PROFESSIONAL);
        String token = tokenService.createToken(professional);

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .param("from", "proximo-lunes")
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agendaService);
    }

    // ================= Escenario 3: agenda libre =================

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda sin clientes programados
    void sinReservasDevuelveContenidoVacioYMensajeInformativo() throws Exception {
        // Arrange
        UserAccount professional = createAccount(UserRole.PROFESSIONAL);
        String token = tokenService.createToken(professional);
        LocalDate today = LocalDate.of(2026, 11, 22);
        when(agendaService.find(eq(professional.getId()), any(), any()))
                .thenReturn(new ProfessionalAgendaResponse(today, today, List.of(),
                        ProfessionalAgendaService.MESSAGE_AGENDA_LIBRE_HOY));

        // Act
        mockMvc.perform(get(ENDPOINT).header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.message")
                        .value("Tu agenda se encuentra libre para la jornada de hoy"));
    }

    // ================= Seguridad =================

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda de atencion diaria
    void rechazaElAccesoSinToken() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(agendaService);
    }

    @Test
    // "Mi Agenda" es del profesional: un cliente no puede consultarla.
    void rechazaElAccesoDeUnClienteDevuelve403() throws Exception {
        UserAccount customer = createAccount(UserRole.CUSTOMER);

        mockMvc.perform(get(ENDPOINT)
                        .header("Authorization", "Bearer " + tokenService.createToken(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(agendaService);
    }

    @Test
    // Decision de negocio: el administrador SI puede consultar la agenda de un profesional.
    void elAdministradorConsultaLaAgendaDeUnProfesionalIndicandoSuId() throws Exception {
        // Arrange
        UserAccount admin = createAccount(UserRole.ADMIN);
        UserAccount professional = createAccount(UserRole.PROFESSIONAL);
        professionalRepository.save(new Professional(professional.getId(), "General"));
        LocalDate today = LocalDate.of(2026, 11, 22);
        when(agendaService.find(eq(professional.getId()), any(), any()))
                .thenReturn(new ProfessionalAgendaResponse(today, today, List.of(
                        item(LocalDateTime.of(2026, 11, 22, 9, 0), "Ana Cliente", "Consulta")),
                        null));

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .param("professionalId", professional.getId().toString())
                        .header("Authorization", "Bearer " + tokenService.createToken(admin)))
                // Assert
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(agendaService).find(professional.getId(), null, null);
    }

    @Test
    // El administrador no tiene agenda propia: debe indicar de que profesional es.
    void elAdministradorSinProfessionalIdRecibe400() throws Exception {
        UserAccount admin = createAccount(UserRole.ADMIN);

        mockMvc.perform(get(ENDPOINT)
                        .header("Authorization", "Bearer " + tokenService.createToken(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(agendaService);
    }

    @Test
    void elAdministradorConUnProfesionalInexistenteRecibe404() throws Exception {
        UserAccount admin = createAccount(UserRole.ADMIN);

        mockMvc.perform(get(ENDPOINT)
                        .param("professionalId", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + tokenService.createToken(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PROFESSIONAL_NOT_FOUND"));

        verifyNoInteractions(agendaService);
    }

    @Test
    void elAdministradorConUnIdentificadorInvalidoRecibe400() throws Exception {
        UserAccount admin = createAccount(UserRole.ADMIN);

        mockMvc.perform(get(ENDPOINT)
                        .param("professionalId", "no-es-un-uuid")
                        .header("Authorization", "Bearer " + tokenService.createToken(admin)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agendaService);
    }

    @Test
    // La ruta antigua (obsoleta) sigue disponible porque el frontend la usa; no se confunde con la agenda.
    void laRutaAntiguaConIdentificadorSigueDisponibleYNoUsaLaAgenda() throws Exception {
        UserAccount professional = createAccount(UserRole.PROFESSIONAL);

        mockMvc.perform(get("/api/v1/reservas/profesional/{id}", professional.getId())
                        .header("Authorization",
                                "Bearer " + tokenService.createToken(professional)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verifyNoInteractions(agendaService);
    }

    private ProfessionalAgendaResponse.Item item(LocalDateTime start, String customerName,
                                                 String serviceName) {
        return new ProfessionalAgendaResponse.Item(UUID.randomUUID(), start, start.plusHours(1),
                customerName, serviceName, "CONFIRMADA");
    }

    private UserAccount createAccount(UserRole role) {
        return accountRepository.save(new UserAccount(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
                "hashed-password",
                "Usuario prueba HU14",
                role));
    }
}
