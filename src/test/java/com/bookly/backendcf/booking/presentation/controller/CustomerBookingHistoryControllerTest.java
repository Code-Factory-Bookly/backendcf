package com.bookly.backendcf.booking.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.bookly.backendcf.booking.application.CustomerBookingHistoryService;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.presentation.dto.BookingHistoryPageResponse;
import com.bookly.backendcf.booking.presentation.dto.BookingResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas del endpoint de historial de reservas del cliente (HU-13).
 *
 * <p>La ruta {@code /api/v1/reservas/cliente/historial} no recibe el identificador del cliente:
 * se resuelve desde el token JWT. Por eso un cliente no puede pedir el historial de otro.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class CustomerBookingHistoryControllerTest {

    private static final String ENDPOINT = "/api/v1/reservas/cliente/historial";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private CustomerBookingHistoryService customerBookingHistoryService;

    // ================= Escenario 1: cliente con reservas =================

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El panel "Mis Reservas" muestra fecha, servicio, profesional y estado.
    void devuelveElHistorialDelClienteAutenticado() throws Exception {
        // Arrange
        UserAccount customer = createAccount(UserRole.CUSTOMER);
        String token = tokenService.createToken(customer);

        LocalDateTime start = LocalDateTime.now().plusDays(1);
        UUID professionalId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        Booking booking = new Booking(
                customer.getId(),
                professionalId,
                serviceId,
                start,
                start.plusHours(1)
        );
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
        booking.setStatus(BookingStatus.CONFIRMADA);

        BookingResponse content = BookingResponse.from(
                booking,
                "Cliente Uno",
                "Profesional Uno",
                "Corte de cabello"
        );

        when(customerBookingHistoryService.find(
                eq(customer.getId()), any(), any(), any(), any(Integer.class),
                any(Integer.class), any()))
                .thenReturn(new BookingHistoryPageResponse(
                        List.of(content), 0, 50, 1, 1, null));

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].startTime").exists())
                .andExpect(jsonPath("$.content[0].serviceName").value("Corte de cabello"))
                .andExpect(jsonPath("$.content[0].professionalName").value("Profesional Uno"))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.content[0].customerId")
                        .value(customer.getId().toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El historial incluye reservas confirmadas, canceladas e historicas.
    void devuelveLasReservasConfirmadasCanceladasEHistoricas() throws Exception {
        // Arrange
        UserAccount customer = createAccount(UserRole.CUSTOMER);
        String token = tokenService.createToken(customer);

        LocalDateTime past = LocalDateTime.now().minusDays(15);
        LocalDateTime future = LocalDateTime.now().plusDays(15);

        BookingResponse confirmada = bookingResponse(customer.getId(), future, BookingStatus.CONFIRMADA);
        BookingResponse cancelada = bookingResponse(customer.getId(), future.plusDays(1), BookingStatus.CANCELADA);
        BookingResponse completada = bookingResponse(customer.getId(), past, BookingStatus.COMPLETED);

        when(customerBookingHistoryService.find(
                eq(customer.getId()), any(), any(), any(), any(Integer.class),
                any(Integer.class), any()))
                .thenReturn(new BookingHistoryPageResponse(
                        List.of(confirmada, cancelada, completada), 0, 50, 3, 1, null));

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.content[1].status").value("CANCELADA"))
                .andExpect(jsonPath("$.content[2].status").value("COMPLETED"));
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // CRITICO: el identificador del cliente se toma de la autenticacion, no de la peticion.
    void tomaElIdentificadorDelClienteDeLaAutenticacion() throws Exception {
        // Arrange
        UserAccount customer = createAccount(UserRole.CUSTOMER);
        String token = tokenService.createToken(customer);

        when(customerBookingHistoryService.find(
                eq(customer.getId()), any(), any(), any(), any(Integer.class),
                any(Integer.class), any()))
                .thenReturn(new BookingHistoryPageResponse(
                        List.of(), 0, 50, 0, 0,
                                CustomerBookingHistoryService.MESSAGE_SIN_RESERVAS));

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isOk());

        verify(customerBookingHistoryService).find(
                eq(customer.getId()),
                any(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any());
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // CRITICO: un cliente NO puede pedir el historial de otro cliente.
    // La ruta no acepta customerId, por lo que cualquier intento se ignora y se
    // devuelve siempre el historial del cliente autenticado.
    void rechazaElHistorialDeOtroCliente() throws Exception {
        // Arrange
        UserAccount customer = createAccount(UserRole.CUSTOMER);
        UserAccount anotherCustomer = createAccount(UserRole.CUSTOMER);
        String token = tokenService.createToken(customer);

        when(customerBookingHistoryService.find(
                eq(customer.getId()), any(), any(), any(), any(Integer.class),
                any(Integer.class), any()))
                .thenReturn(new BookingHistoryPageResponse(
                        List.of(), 0, 50, 0, 0,
                                CustomerBookingHistoryService.MESSAGE_SIN_RESERVAS));

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .param("customerId", anotherCustomer.getId().toString())
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isOk());

        verify(customerBookingHistoryService).find(
                eq(customer.getId()),
                any(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any());

        verify(customerBookingHistoryService, never()).find(
                eq(anotherCustomer.getId()),
                any(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any());
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // Los filtros opcionales de la ruta se delegan al caso de uso.
    void delegaLosFiltrosDeFechaEstadoYOrdenAlCasoDeUso() throws Exception {
        // Arrange
        UserAccount customer = createAccount(UserRole.CUSTOMER);
        String token = tokenService.createToken(customer);

        LocalDateTime from = LocalDateTime.now().minusDays(30);
        LocalDateTime to = LocalDateTime.now().plusDays(30);

        when(customerBookingHistoryService.find(
                any(), any(), any(), any(), any(Integer.class),
                any(Integer.class), any()))
                .thenReturn(new BookingHistoryPageResponse(
                        List.of(), 0, 50, 0, 0,
                                CustomerBookingHistoryService.MESSAGE_SIN_RESERVAS));

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("status", BookingStatus.CANCELADA.name())
                        .param("page", "1")
                        .param("size", "20")
                        .param("order", "desc")
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isOk());

        verify(customerBookingHistoryService).find(
                eq(customer.getId()),
                eq(from),
                eq(to),
                eq(BookingStatus.CANCELADA),
                eq(1),
                eq(20),
                eq("desc"));
    }

    // ================= QA/Exception: cliente sin reservas =================

    @Test
    // QA/Exception: Sin reservas registradas
    void devuelveElHistorialVacioConMensajeInformativo() throws Exception {
        // Arrange
        UserAccount customer = createAccount(UserRole.CUSTOMER);
        String token = tokenService.createToken(customer);

        when(customerBookingHistoryService.find(
                eq(customer.getId()), any(), any(), any(), any(Integer.class),
                any(Integer.class), any()))
                .thenReturn(new BookingHistoryPageResponse(
                        List.of(), 0, 50, 0, 0,
                                CustomerBookingHistoryService.MESSAGE_SIN_RESERVAS));

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.message")
                        .value(CustomerBookingHistoryService.MESSAGE_SIN_RESERVAS));
    }

    // ================= Validaciones =================

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    void rechazaElHistorialConPaginaNegativa() throws Exception {
        // Arrange
        UserAccount customer = createAccount(UserRole.CUSTOMER);
        String token = tokenService.createToken(customer);

        when(customerBookingHistoryService.find(
                any(), any(), any(), any(), any(Integer.class),
                any(Integer.class), any()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "La página no puede ser negativa"));

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .param("page", "-1")
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("La página no puede ser negativa"));
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    void rechazaElHistorialConOrdenInvalido() throws Exception {
        // Arrange
        UserAccount customer = createAccount(UserRole.CUSTOMER);
        String token = tokenService.createToken(customer);

        when(customerBookingHistoryService.find(
                any(), any(), any(), any(), any(Integer.class),
                any(Integer.class), any()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "El orden debe ser asc o desc"));

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .param("order", "lateral")
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El orden debe ser asc o desc"));
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    void rechazaElHistorialConTamanoDePaginaMayorAlMaximo() throws Exception {
        // Arrange
        UserAccount customer = createAccount(UserRole.CUSTOMER);
        String token = tokenService.createToken(customer);

        when(customerBookingHistoryService.find(
                any(), any(), any(), any(), any(Integer.class),
                any(Integer.class), any()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "El tamaño de página debe estar entre 1 y 100"));

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .param("size", "101")
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ================= Seguridad =================

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    void rechazaElAccesoSinToken() throws Exception {
        // Act
        mockMvc.perform(get(ENDPOINT))
                // Assert
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(customerBookingHistoryService);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El endpoint solo permite el rol CUSTOMER.
    void rechazaElAccesoDeUnProfesionalDevuelve403() throws Exception {
        // Arrange
        UserAccount professional = createAccount(UserRole.PROFESSIONAL);
        String token = tokenService.createToken(professional);

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(customerBookingHistoryService);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El endpoint solo permite el rol CUSTOMER.
    void rechazaElAccesoDeUnAdministradorDevuelve403() throws Exception {
        // Arrange
        UserAccount admin = createAccount(UserRole.ADMIN);
        String token = tokenService.createToken(admin);

        // Act
        mockMvc.perform(get(ENDPOINT)
                        .header("Authorization", "Bearer " + token))
                // Assert
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(customerBookingHistoryService);
    }

    private BookingResponse bookingResponse(
            UUID customerId,
            LocalDateTime start,
            BookingStatus status) {
        Booking booking = new Booking(
                customerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                start,
                start.plusHours(1)
        );
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
        booking.setStatus(status);
        return BookingResponse.from(booking, "Cliente Uno", "Profesional Uno", "Servicio Uno");
    }

    private UserAccount createAccount(UserRole role) {
        UserAccount account = new UserAccount(
                role.name().toLowerCase()
                        + "-"
                        + UUID.randomUUID()
                        + "@example.com",
                "hashed-password",
                "Usuario prueba HU13",
                role
        );

        return accountRepository.save(account);
    }
}
