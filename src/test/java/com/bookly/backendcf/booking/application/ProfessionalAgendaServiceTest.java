package com.bookly.backendcf.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.booking.presentation.dto.ProfessionalAgendaResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pruebas del caso de uso "Mi Agenda" del profesional (HU-14).
 *
 * <p>El servicio recibe el identificador del profesional ya resuelto desde el token: es la
 * frontera que garantiza que solo se consulta la agenda propia. "Hoy" se calcula en la zona
 * horaria de Colombia (GMT-5), no en la del servidor.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ProfessionalAgendaServiceTest {

    // 22/11/2026 22:30 en Colombia = 23/11/2026 03:30 UTC. Un servidor en UTC ya estaria en
    // el dia siguiente; la agenda de "hoy" debe seguir siendo la del 22.
    private static final Instant NOW = Instant.parse("2026-11-23T03:30:00Z");
    private static final LocalDate TODAY_IN_COLOMBIA = LocalDate.of(2026, 11, 22);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingNameDirectory nameDirectory;

    private ProfessionalAgendaService service;
    private final UUID professionalId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProfessionalAgendaService(
                bookingRepository, nameDirectory, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ================= Escenario 1: agenda del dia =================

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda de atencion diaria
    void sinParametrosConsultaElDiaDeHoyEnZonaHorariaDeColombia() {
        // Arrange
        when(bookingRepository.findAgenda(professionalId,
                LocalDateTime.of(2026, 11, 22, 0, 0), LocalDateTime.of(2026, 11, 23, 0, 0)))
                .thenReturn(List.of());

        // Act
        ProfessionalAgendaResponse response = service.find(professionalId, null, null);

        // Assert
        assertThat(response.from()).isEqualTo(TODAY_IN_COLOMBIA);
        assertThat(response.to()).isEqualTo(TODAY_IN_COLOMBIA);
        verify(bookingRepository).findAgenda(professionalId,
                LocalDateTime.of(2026, 11, 22, 0, 0), LocalDateTime.of(2026, 11, 23, 0, 0));
    }

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda de atencion diaria
    // Hora, nombre del cliente y tipo de servicio, en el orden cronologico del repositorio.
    void devuelveHoraClienteYServicioDeCadaReserva() {
        // Arrange
        UUID customerA = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();
        UUID serviceA = UUID.randomUUID();
        UUID serviceB = UUID.randomUUID();
        Booking morning = booking(customerA, serviceA, LocalDateTime.of(2026, 11, 22, 9, 0),
                BookingStatus.CONFIRMADA);
        Booking afternoon = booking(customerB, serviceB, LocalDateTime.of(2026, 11, 22, 15, 0),
                BookingStatus.COMPLETED);
        when(bookingRepository.findAgenda(professionalId,
                LocalDateTime.of(2026, 11, 22, 0, 0), LocalDateTime.of(2026, 11, 23, 0, 0)))
                .thenReturn(List.of(morning, afternoon));
        when(nameDirectory.accountNames(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of(customerA, "Ana Cliente", customerB, "Beto Cliente"));
        when(nameDirectory.serviceNames(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of(serviceA, "Consulta general", serviceB, "Control"));

        // Act
        ProfessionalAgendaResponse response = service.find(professionalId, null, null);

        // Assert
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).startTime())
                .isEqualTo(LocalDateTime.of(2026, 11, 22, 9, 0));
        assertThat(response.content().get(0).customerName()).isEqualTo("Ana Cliente");
        assertThat(response.content().get(0).serviceName()).isEqualTo("Consulta general");
        assertThat(response.content().get(0).status()).isEqualTo("CONFIRMADA");
        assertThat(response.content().get(1).startTime())
                .isEqualTo(LocalDateTime.of(2026, 11, 22, 15, 0));
        assertThat(response.content().get(1).customerName()).isEqualTo("Beto Cliente");
        assertThat(response.content().get(1).serviceName()).isEqualTo("Control");
        assertThat(response.message()).isNull();
    }

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda de atencion diaria
    // El servicio siempre consulta con el id recibido (el del token), nunca con otro.
    void consultaUnicamenteConElIdentificadorDelProfesionalAutenticado() {
        // Arrange
        when(bookingRepository.findAgenda(professionalId,
                LocalDateTime.of(2026, 11, 22, 0, 0), LocalDateTime.of(2026, 11, 23, 0, 0)))
                .thenReturn(List.of());

        // Act
        service.find(professionalId, null, null);

        // Assert
        verify(bookingRepository).findAgenda(
                org.mockito.ArgumentMatchers.eq(professionalId),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // ================= Escenario 2: rango de fechas =================

    @Test
    // Escenario Gherkin HU-14: Consultar agenda en un rango de fechas ampliado
    // Lunes a viernes de la proxima semana: el viernes se incluye completo.
    void conRangoIncluyeElDiaFinalCompleto() {
        // Arrange
        LocalDate monday = LocalDate.of(2026, 11, 30);
        LocalDate friday = LocalDate.of(2026, 12, 4);
        Booking booking = booking(UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.of(2026, 12, 4, 23, 30), BookingStatus.CONFIRMADA);
        when(bookingRepository.findAgenda(professionalId,
                LocalDateTime.of(2026, 11, 30, 0, 0), LocalDateTime.of(2026, 12, 5, 0, 0)))
                .thenReturn(List.of(booking));
        when(nameDirectory.accountNames(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of());
        when(nameDirectory.serviceNames(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of());

        // Act
        ProfessionalAgendaResponse response = service.find(professionalId, monday, friday);

        // Assert
        assertThat(response.from()).isEqualTo(monday);
        assertThat(response.to()).isEqualTo(friday);
        assertThat(response.content()).hasSize(1);
        assertThat(response.message()).isNull();
    }

    @Test
    // Escenario Gherkin HU-14: Consultar agenda en un rango de fechas ampliado
    // Con solo "from" se consulta ese dia.
    void conSoloFromConsultaEseDia() {
        // Arrange
        LocalDate day = LocalDate.of(2026, 11, 25);
        when(bookingRepository.findAgenda(professionalId,
                LocalDateTime.of(2026, 11, 25, 0, 0), LocalDateTime.of(2026, 11, 26, 0, 0)))
                .thenReturn(List.of());

        // Act
        ProfessionalAgendaResponse response = service.find(professionalId, day, null);

        // Assert
        assertThat(response.from()).isEqualTo(day);
        assertThat(response.to()).isEqualTo(day);
    }

    @Test
    // Escenario Gherkin HU-14: Consultar agenda en un rango de fechas ampliado
    void rechazaUnRangoConFechaInicialPosteriorALaFinal() {
        assertThatThrownBy(() -> service.find(professionalId,
                LocalDate.of(2026, 12, 4), LocalDate.of(2026, 11, 30)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason())
                            .isEqualTo("La fecha inicial no puede ser posterior a la fecha final");
                });

        verifyNoInteractions(bookingRepository);
    }

    @Test
    // Escenario Gherkin HU-14: Consultar agenda en un rango de fechas ampliado
    // Solo "to" anterior a hoy implica un "from" (hoy) posterior al "to".
    void rechazaSoloToAnteriorAHoy() {
        assertThatThrownBy(() -> service.find(professionalId, null, LocalDate.of(2026, 11, 20)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(bookingRepository);
    }

    @Test
    // El listado no esta paginado, asi que el rango maximo es de 31 dias.
    void rechazaUnRangoMayorAlMaximoPermitido() {
        assertThatThrownBy(() -> service.find(professionalId,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 2)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("31");
                });

        verifyNoInteractions(bookingRepository);
    }

    @Test
    // 31 dias exactos (1 al 31 de diciembre) si se permite.
    void permiteUnRangoDeExactamenteElMaximo() {
        when(bookingRepository.findAgenda(professionalId,
                LocalDateTime.of(2026, 12, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0)))
                .thenReturn(List.of());

        ProfessionalAgendaResponse response = service.find(professionalId,
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));

        assertThat(response.content()).isEmpty();
    }

    // ================= Escenario 3: agenda libre =================

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda sin clientes programados
    void sinReservasHoyInformaQueLaAgendaEstaLibreParaLaJornadaDeHoy() {
        // Arrange
        when(bookingRepository.findAgenda(professionalId,
                LocalDateTime.of(2026, 11, 22, 0, 0), LocalDateTime.of(2026, 11, 23, 0, 0)))
                .thenReturn(List.of());

        // Act
        ProfessionalAgendaResponse response = service.find(professionalId, null, null);

        // Assert
        assertThat(response.content()).isEmpty();
        assertThat(response.message()).isEqualTo(ProfessionalAgendaService.MESSAGE_AGENDA_LIBRE_HOY);
        assertThat(ProfessionalAgendaService.MESSAGE_AGENDA_LIBRE_HOY)
                .isEqualTo("Tu agenda se encuentra libre para la jornada de hoy");
    }

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda sin clientes programados
    // Si el dia consultado no es hoy, el mensaje no puede decir "hoy".
    void sinReservasEnOtroPeriodoUsaUnMensajeQueNoDiceHoy() {
        // Arrange
        when(bookingRepository.findAgenda(professionalId,
                LocalDateTime.of(2026, 11, 30, 0, 0), LocalDateTime.of(2026, 12, 5, 0, 0)))
                .thenReturn(List.of());

        // Act
        ProfessionalAgendaResponse response = service.find(professionalId,
                LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 4));

        // Assert
        assertThat(response.message()).isEqualTo(ProfessionalAgendaService.MESSAGE_AGENDA_LIBRE_PERIODO);
        assertThat(response.message()).doesNotContain("hoy");
    }

    @Test
    // El dia de hoy no se reconoce por "ser un solo dia", sino por ser realmente hoy en Colombia.
    void unSoloDiaDistintoDeHoyNoUsaElMensajeDeHoy() {
        when(bookingRepository.findAgenda(professionalId,
                LocalDateTime.of(2026, 11, 23, 0, 0), LocalDateTime.of(2026, 11, 24, 0, 0)))
                .thenReturn(List.of());

        ProfessionalAgendaResponse response = service.find(professionalId,
                LocalDate.of(2026, 11, 23), null);

        assertThat(response.message()).isEqualTo(ProfessionalAgendaService.MESSAGE_AGENDA_LIBRE_PERIODO);
    }

    // ================= Identidad =================

    @Test
    void rechazaUnProfesionalSinIdentidad() {
        assertThatThrownBy(() -> service.find(null, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(bookingRepository);
    }

    @Test
    void laZonaHorariaDeLaAgendaEsColombia() {
        assertThat(ProfessionalAgendaService.AGENDA_ZONE).isEqualTo(ZoneId.of("America/Bogota"));
        assertThat(ProfessionalAgendaService.AGENDA_ZONE.getRules().getOffset(NOW))
                .isEqualTo(ZoneOffset.ofHours(-5));
    }

    private Booking booking(UUID customerId, UUID serviceId, LocalDateTime start,
                            BookingStatus status) {
        Booking booking = new Booking(customerId, professionalId, serviceId, start,
                start.plusHours(1));
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
        booking.setStatus(status);
        return booking;
    }
}
