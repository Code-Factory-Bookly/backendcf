package com.bookly.backendcf.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.booking.presentation.dto.BookingHistoryPageResponse;
import com.bookly.backendcf.booking.presentation.dto.BookingResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pruebas del caso de uso de historial de reservas del cliente (HU-13).
 *
 * <p>El servicio es la frontera que garantiza el aislamiento entre clientes: recibe el
 * identificador del cliente ya resuelto (desde el token) y nunca lo toma de la peticion.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CustomerBookingHistoryServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingNameDirectory nameDirectory;

    private CustomerBookingHistoryService service;

    private UUID customerId;
    private UUID otherCustomerId;
    private UUID professionalId;
    private UUID serviceId;

    @BeforeEach
    void setUp() {
        service = new CustomerBookingHistoryService(
                bookingRepository,
                nameDirectory
        );

        customerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();
        professionalId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
    }

    // ================= Escenario 1: cliente con reservas =================

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    void devuelveElHistorialDelClienteAutenticado() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Booking booking = booking(customerId, start, BookingStatus.CONFIRMADA);
        Pageable pageable = PageRequest.of(0, 50);

        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(booking), pageable, 1));
        when(nameDirectory.accountNames(any()))
                .thenReturn(Map.of(
                        customerId, "Cliente Uno",
                        professionalId, "Profesional Uno"));
        when(nameDirectory.serviceNames(any()))
                .thenReturn(Map.of(serviceId, "Corte de cabello"));

        // Act
        BookingHistoryPageResponse response = service.find(
                customerId,
                null,
                null,
                null,
                0,
                50,
                "asc");

        // Assert
        assertThat(response.content()).hasSize(1);
        BookingResponse first = response.content().get(0);
        assertThat(first.customerId()).isEqualTo(customerId);
        assertThat(first.customerName()).isEqualTo("Cliente Uno");
        assertThat(first.professionalName()).isEqualTo("Profesional Uno");
        assertThat(first.serviceName()).isEqualTo("Corte de cabello");
        assertThat(first.status()).isEqualTo("CONFIRMADA");
        assertThat(first.startTime()).isEqualTo(start);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.message()).isNull();
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El historial incluye reservas confirmadas, canceladas e historicas.
    void incluyeReservasConfirmadasCanceladasEHistoricas() {
        // Arrange
        LocalDateTime past = LocalDateTime.now().minusDays(10);
        LocalDateTime future = LocalDateTime.now().plusDays(10);

        Booking confirmed = booking(customerId, future, BookingStatus.CONFIRMADA);
        Booking cancelled = booking(customerId, future.plusDays(1), BookingStatus.CANCELADA);
        Booking completed = booking(customerId, past, BookingStatus.COMPLETED);

        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(confirmed, cancelled, completed),
                        PageRequest.of(0, 50),
                        3));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        BookingHistoryPageResponse response = service.find(
                customerId, null, null, null, 0, 50, "asc");

        // Assert
        assertThat(response.content())
                .extracting(BookingResponse::status)
                .containsExactly("CONFIRMADA", "CANCELADA", "COMPLETED");
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El servicio consulta el historial del cliente que se le indica y no el de otro:
    // el identificador se recibe ya resuelto y nunca se toma de la peticion.
    void consultaElHistorialDelClienteSolicitado() {
        // Arrange
        when(bookingRepository.findCustomerHistory(
                eq(otherCustomerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        // Act
        BookingHistoryPageResponse response = service.find(
                otherCustomerId, null, null, null, 0, 50, "asc");

        // Assert
        assertThat(response.content()).isEmpty();
        verify(bookingRepository).findCustomerHistory(
                eq(otherCustomerId), any(), any(), any(), any());
        verify(bookingRepository, never()).findCustomerHistory(
                eq(customerId), any(), any(), any(), any());
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El criterio de orden es startTime y, a igual fecha, el identificador (orden estable).
    void ordenaPorFechaDeInicioYLuegoPorIdentificador() {
        // Arrange
        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        service.find(customerId, null, null, null, 0, 50, "asc");

        // Assert
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingRepository).findCustomerHistory(
                eq(customerId), any(), any(), any(), captor.capture());

        Pageable pageable = captor.getValue();
        assertThat(pageable.getSort().getOrderFor("startTime")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("startTime").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getSort().getOrderFor("id").getNullHandling())
                .isEqualTo(Sort.NullHandling.NATIVE);
        assertThat(pageable.getSort().getOrderFor("id").isAscending()).isTrue();
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    void aplicaElOrdenDescendente() {
        // Arrange
        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        service.find(customerId, null, null, null, 0, 50, "desc");

        // Assert
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingRepository).findCustomerHistory(
                eq(customerId), any(), any(), any(), captor.capture());

        Sort sort = captor.getValue().getSort();
        assertThat(sort.getOrderFor("startTime")).isNotNull();
        assertThat(sort.getOrderFor("startTime").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("id")).isNotNull();
        assertThat(sort.getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // Sin orden explicito el historial se presenta de forma cronologica ascendente.
    void aplicaElOrdenAscendentePorDefecto() {
        // Arrange
        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        service.find(customerId, null, null, null, 0, 50, null);

        // Assert
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingRepository).findCustomerHistory(
                eq(customerId), any(), any(), any(), captor.capture());

        assertThat(captor.getValue().getSort().getOrderFor("startTime").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // Los filtros opcionales se delegan al repositorio sin alterarlos.
    void delegaLosFiltrosDeFechaYEstadoAlRepositorio() {
        // Arrange
        LocalDateTime from = LocalDateTime.now().minusDays(5);
        LocalDateTime to = LocalDateTime.now().plusDays(5);

        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        service.find(
                customerId,
                from,
                to,
                BookingStatus.CANCELADA,
                0,
                50,
                "asc");

        // Assert
        verify(bookingRepository).findCustomerHistory(
                eq(customerId),
                eq(from),
                eq(to),
                eq(BookingStatus.CANCELADA),
                any(Pageable.class));
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    void conservaLosMetadatosDePaginacion() {
        // Arrange
        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(),
                        PageRequest.of(2, 10),
                        45));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        BookingHistoryPageResponse response = service.find(
                customerId, null, null, null, 2, 10, "asc");

        // Assert
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(45);
        assertThat(response.totalPages()).isEqualTo(5);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    void resuelveLosNombresDeServicioYProfesional() {
        // Arrange
        Booking booking = booking(customerId, LocalDateTime.now().plusDays(1), BookingStatus.CONFIRMADA);

        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(booking), PageRequest.of(0, 50), 1));
        when(nameDirectory.accountNames(any()))
                .thenReturn(Map.of(
                        customerId, "Cliente Dos",
                        professionalId, "Profesional Dos"));
        when(nameDirectory.serviceNames(any()))
                .thenReturn(Map.of(serviceId, "Manicure"));

        // Act
        BookingHistoryPageResponse response = service.find(
                customerId, null, null, null, 0, 50, "asc");

        // Assert
        BookingResponse first = response.content().get(0);
        assertThat(first.customerName()).isEqualTo("Cliente Dos");
        assertThat(first.professionalName()).isEqualTo("Profesional Dos");
        assertThat(first.serviceName()).isEqualTo("Manicure");
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // Los nombres ausentes no rompen la respuesta: quedan en null.
    void dejaEnNullLosNombresNoResueltos() {
        // Arrange
        Booking booking = booking(customerId, LocalDateTime.now().plusDays(1), BookingStatus.CONFIRMADA);

        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(booking), PageRequest.of(0, 50), 1));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        BookingHistoryPageResponse response = service.find(
                customerId, null, null, null, 0, 50, "asc");

        // Assert
        BookingResponse first = response.content().get(0);
        assertThat(first.customerName()).isNull();
        assertThat(first.professionalName()).isNull();
        assertThat(first.serviceName()).isNull();
    }

    // ================= QA/Exception: cliente sin reservas =================

    @Test
    // QA/Exception: Sin reservas registradas
    void muestraElMensajeDeAusenciaDeReservas() {
        // Arrange
        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        BookingHistoryPageResponse response = service.find(
                customerId, null, null, null, 0, 50, "asc");

        // Assert
        assertThat(response.content()).isEmpty();
        assertThat(response.message())
                .isEqualTo(CustomerBookingHistoryService.MESSAGE_SIN_RESERVAS);
        assertThat(response.totalElements()).isZero();
    }

    // ================= Validaciones =================

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // Sin identidad de cliente no se puede construir el historial.
    void rechazaElHistorialSinIdentidadDeCliente() {
        // Arrange
        // Act
        // Assert
        assertThatThrownBy(() -> service.find(
                null, null, null, null, 0, 50, "asc"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException statusException =
                            (ResponseStatusException) exception;
                    assertThat(statusException.getStatusCode())
                            .isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(statusException.getReason())
                            .contains("identidad de cliente");
                });

        verifyNoInteractions(bookingRepository);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    void rechazaElHistorialConRangoDeFechasInvalido() {
        // Arrange
        LocalDateTime from = LocalDateTime.now().plusDays(2);
        LocalDateTime to = LocalDateTime.now().plusDays(1);

        // Act
        // Assert
        assertThatThrownBy(() -> service.find(
                customerId, from, to, null, 0, 50, "asc"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException statusException =
                            (ResponseStatusException) exception;
                    assertThat(statusException.getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(statusException.getReason())
                            .contains("fecha inicial");
                });

        verifyNoInteractions(bookingRepository);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // Un rango con la misma fecha inicial y final es valido (rango inclusivo).
    void aceptaElHistorialConRangoDeFechasIguales() {
        // Arrange
        LocalDateTime sameMoment = LocalDateTime.now().plusDays(1);

        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        BookingHistoryPageResponse response = service.find(
                customerId, sameMoment, sameMoment, null, 0, 50, "asc");

        // Assert
        assertThat(response.content()).isEmpty();
        verify(bookingRepository).findCustomerHistory(
                eq(customerId), eq(sameMoment), eq(sameMoment), any(), any());
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // Indicar solo una de las dos fechas no invalida la consulta: el filtro
    // ausente se deja abierto y la base lo resuelve.
    void aceptaElHistorialConSoloUnaDeLasFechas() {
        // Arrange
        LocalDateTime from = LocalDateTime.now().minusDays(5);

        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        BookingHistoryPageResponse response = service.find(
                customerId, from, null, null, 0, 50, "asc");

        // Assert
        assertThat(response.content()).isEmpty();
        verify(bookingRepository).findCustomerHistory(
                eq(customerId), eq(from), eq(null), any(), any());
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    void rechazaElHistorialConPaginaNegativa() {
        // Arrange
        // Act
        // Assert
        assertThatThrownBy(() -> service.find(
                customerId, null, null, null, -1, 50, "asc"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException statusException =
                            (ResponseStatusException) exception;
                    assertThat(statusException.getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(statusException.getReason())
                            .contains("página no puede ser negativa");
                });

        verifyNoInteractions(bookingRepository);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    void rechazaElHistorialConTamanoDePaginaCero() {
        // Arrange
        // Act
        // Assert
        assertThatThrownBy(() -> service.find(
                customerId, null, null, null, 0, 0, "asc"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException statusException =
                            (ResponseStatusException) exception;
                    assertThat(statusException.getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(statusException.getReason())
                            .contains("entre 1 y 100");
                });

        verifyNoInteractions(bookingRepository);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El tamano de pagina no puede superar el maximo de 100.
    void rechazaElHistorialConTamanoDePaganaMayorAlMaximo() {
        // Arrange
        // Act
        // Assert
        assertThatThrownBy(() -> service.find(
                customerId, null, null, null, 0, 101, "asc"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException statusException =
                            (ResponseStatusException) exception;
                    assertThat(statusException.getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(statusException.getReason())
                            .contains("entre 1 y 100");
                });

        verifyNoInteractions(bookingRepository);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El tamano de pagina maximo (100) si se acepta.
    void aceptaElTamanoDePaginaMaximoDe100() {
        // Arrange
        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        BookingHistoryPageResponse response = service.find(
                customerId, null, null, null, 0, 100, "asc");

        // Assert
        assertThat(response.size()).isEqualTo(100);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingRepository).findCustomerHistory(
                eq(customerId), any(), any(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    void rechazaElHistorialConOrdenInvalido() {
        // Arrange
        // Act
        // Assert
        assertThatThrownBy(() -> service.find(
                customerId, null, null, null, 0, 50, "lateral"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException statusException =
                            (ResponseStatusException) exception;
                    assertThat(statusException.getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(statusException.getReason())
                            .contains("asc o desc");
                });

        verifyNoInteractions(bookingRepository);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // Un orden en blanco equivale a no indicar orden: se ordena ascendente.
    void aceptaElOrdenEnBlancoComoAscendente() {
        // Arrange
        when(bookingRepository.findCustomerHistory(
                eq(customerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        service.find(customerId, null, null, null, 0, 50, "   ");

        // Assert
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingRepository).findCustomerHistory(
                eq(customerId), any(), any(), any(), captor.capture());

        assertThat(captor.getValue().getSort().getOrderFor("startTime").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    private Booking booking(UUID ownerCustomerId, LocalDateTime start, BookingStatus status) {
        Booking booking = new Booking(
                ownerCustomerId,
                professionalId,
                serviceId,
                start,
                start.plusHours(1)
        );
        booking.setStatus(status);
        return booking;
    }
}
