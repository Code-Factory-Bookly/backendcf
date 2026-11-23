package com.bookly.backendcf.booking.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.application.service.CreateBookingService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * HU-05: contrato HTTP de la creacion de reservas cuando la franja esta bloqueada.
 *
 * <p>Referencia a los criterios de aceptacion (Gherkin, HU-05 refinada Sprint 2):
 * <ul>
 *   <li>Escenario "Bloquear una franja sin reservas previas": el sistema deja de ofrecer esas
 *       franjas; una reserva sobre un rango bloqueado responde 409 SLOT_BLOCKED.</li>
 * </ul>
 *
 * <p>Reglas y errores: {@code docs/HU-05-bloqueo-agenda.md} (seccion "Efecto en el resto del
 * sistema", Reservas HU-08).
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class BookingBlockedSlotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private CreateBookingService createBookingService;

    private UserAccount customer;
    private UUID professionalId;
    private UUID serviceId;
    private LocalDateTime start;
    private LocalDateTime end;
    private String requestBody;

    @BeforeEach
    void setUp() {
        customer = accountRepository.save(new UserAccount(
                "customer-" + UUID.randomUUID() + "@example.com",
                "hashed-password", "Usuario de prueba", UserRole.CUSTOMER));
        professionalId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        start = LocalDateTime.now().plusDays(30).withHour(9).withMinute(0).withSecond(0).withNano(0);
        end = start.plusHours(1);
        DateTimeFormatter iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        requestBody = "{\"professionalId\":\"" + professionalId + "\","
                + "\"serviceId\":\"" + serviceId + "\","
                + "\"startTime\":\"" + start.format(iso) + "\","
                + "\"endTime\":\"" + end.format(iso) + "\"}";
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Reservar dentro de un rango bloqueado responde 409 con SLOT_BLOCKED.
    // ------------------------------------------------------------------
    @Test
    void reservaEnUnRangoBloqueadoDevuelveConflictConSlotBlocked() throws Exception {
        // Arrange
        when(createBookingService.createBooking(
                eq(customer.getId()), eq(professionalId), eq(serviceId), eq(start), eq(end)))
                .thenThrow(new BookingConflictException(
                        "SLOT_BLOCKED", "El horario seleccionado está bloqueado por el profesional"));

        // Act
        mockMvc.perform(post("/api/v1/reservas")
                        .header("Authorization", "Bearer " + tokenService.createToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Assert
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SLOT_BLOCKED"));
    }

    // ------------------------------------------------------------------
    // QA/Exception: sin token, crear una reserva responde 401.
    // ------------------------------------------------------------------
    @Test
    void sinTokenNoPuedeCrearUnaReserva() throws Exception {
        // Act
        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Assert
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(createBookingService, never()).createBooking(any(), any(), any(), any(), any());
    }
}
