package com.bookly.backendcf.booking.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.booking.application.CustomerBookingHistoryService;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del DTO de la pagina de historial de reservas (HU-13).
 */
@Tag("unit")
class BookingHistoryPageResponseTest {

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // La pagina expone el contenido junto con los metadatos de paginacion.
    void creaLaPaginaConContenidoYMensajeNulo() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Booking booking = new Booking(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                start,
                start.plusHours(1)
        );
        BookingResponse content = BookingResponse.from(
                booking,
                "Cliente Uno",
                "Profesional Uno",
                "Corte de cabello"
        );

        // Act
        BookingHistoryPageResponse response = new BookingHistoryPageResponse(
                List.of(content),
                0,
                50,
                1,
                1,
                null
        );

        // Assert
        assertThat(response.content()).containsExactly(content);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(50);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.message()).isNull();
    }

    @Test
    // QA/Exception: Sin reservas registradas
    // La pagina vacia informa al cliente que aun no cuenta con reservas.
    void creaLaPaginaVaciaConMensajeInformativo() {
        // Arrange
        String mensaje = CustomerBookingHistoryService.MESSAGE_SIN_RESERVAS;

        // Act
        BookingHistoryPageResponse response = new BookingHistoryPageResponse(
                List.of(),
                0,
                50,
                0,
                0,
                mensaje
        );

        // Assert
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.message())
                .isEqualTo(CustomerBookingHistoryService.MESSAGE_SIN_RESERVAS);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El contenido expone fecha, servicio, profesional y estado de la reserva.
    void exponeLaFechaServicioProfesionalYEstadoDeCadaReserva() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().minusDays(3);
        Booking booking = new Booking(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                start,
                start.plusMinutes(45)
        );
        booking.setStatus(BookingStatus.CANCELADA);

        // Act
        BookingResponse response = BookingResponse.from(
                booking,
                "Cliente Tres",
                "Profesional Tres",
                "Coloración"
        );

        // Assert
        assertThat(response.startTime()).isEqualTo(start);
        assertThat(response.endTime()).isEqualTo(start.plusMinutes(45));
        assertThat(response.serviceName()).isEqualTo("Coloración");
        assertThat(response.professionalName()).isEqualTo("Profesional Tres");
        assertThat(response.customerName()).isEqualTo("Cliente Tres");
        assertThat(response.status()).isEqualTo("CANCELADA");
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // La pagina expone de forma coherente los metadatos de paginacion: el total de
    // elementos debe ser coherente con el contenido y el numero de paginas con el
    // tamano de pagina.
    void exponeMetadatosDePaginacionCoherentes() {
        // Arrange
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 9, 0, 0);
        Booking booking = new Booking(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                start,
                start.plusHours(1)
        );
        BookingResponse content = BookingResponse.from(
                booking,
                "Cliente Uno",
                "Profesional Uno",
                "Corte de cabello"
        );

        // Act
        BookingHistoryPageResponse response = new BookingHistoryPageResponse(
                List.of(content),
                0,
                50,
                1,
                1,
                null
        );

        // Assert
        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements())
                .isEqualTo(response.content().size());
        assertThat(response.totalPages())
                .isEqualTo((int) Math.ceil((double) response.totalElements() / response.size()));
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(50);
    }
}
