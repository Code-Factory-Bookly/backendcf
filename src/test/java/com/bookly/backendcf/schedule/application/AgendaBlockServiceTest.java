package com.bookly.backendcf.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.schedule.domain.model.AgendaBlock;
import com.bookly.backendcf.schedule.infrastructure.persistence.AgendaBlockRepository;
import com.bookly.backendcf.schedule.presentation.dto.AgendaBlockRequest;
import com.bookly.backendcf.schedule.presentation.dto.AgendaBlockResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * HU-05: bloqueos temporales de agenda.
 *
 * <p>Referencia a los criterios de aceptacion (Gherkin, HU-05 refinada Sprint 2):
 * <ul>
 *   <li>Escenario "Bloquear una franja sin reservas previas": el bloqueo se crea y se persiste.</li>
 *   <li>Escenario "Intento de bloquear rango con reservas confirmadas activas": el bloqueo se rechaza
 *       y se presentan las citas afectadas.</li>
 * </ul>
 *
 * <p>Reglas y errores: {@code docs/HU-05-bloqueo-agenda.md}.
 */
@Tag("unit")
class AgendaBlockServiceTest {

    /** Formato de fecha igual al que usa el servicio para describir un rango. */
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("es"));
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final UUID professionalId = UUID.randomUUID();

    /**
     * Rango relativo al momento de ejecucion: el servicio valida contra LocalDateTime.now(),
     * asi que una fecha fija acabaria expirando la suite.
     */
    private LocalDateTime inicio;
    private LocalDateTime fin;

    private AgendaBlockRepository repository;
    private ConfirmedBookingsLookup confirmedBookings;
    private AgendaBlockService service;

    @BeforeEach
    void setUp() {
        inicio = LocalDateTime.now().plusDays(30).withHour(9).withMinute(0).withSecond(0).withNano(0);
        fin = inicio.plusHours(8);
        repository = mock(AgendaBlockRepository.class);
        confirmedBookings = mock(ConfirmedBookingsLookup.class);
        service = new AgendaBlockService(repository, confirmedBookings);
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // ------------------------------------------------------------------
    @Test
    void creaElBloqueoCuandoElRangoEsValido() {
        // Arrange
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.saveAndFlush(any(AgendaBlock.class))).thenAnswer(invocation -> {
            AgendaBlock saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        // Act
        AgendaBlockResponse response = service.create(professionalId, request(inicio, fin, "Vacaciones"));

        // Assert
        assertThat(response.professionalId()).isEqualTo(professionalId);
        assertThat(response.startTime()).isEqualTo(inicio);
        assertThat(response.endTime()).isEqualTo(fin);
        assertThat(response.reason()).isEqualTo("Vacaciones");
        assertThat(response.id()).isNotNull();
        verify(repository).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloqueo con reservas confirmadas activas.
    // El detalle del conflicto nombra a cada cita afectada.
    // ------------------------------------------------------------------
    @Test
    void rechazaElBloqueoCuandoHayReservasConfirmadas() {
        // Arrange
        UUID bookingId = UUID.randomUUID();
        LocalDateTime bookingStart = inicio.plusHours(1);
        LocalDateTime bookingEnd = inicio.plusHours(2);
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of(
                new ConfirmedBookingsLookup.AffectedBooking(
                        bookingId, bookingStart, bookingEnd, "Ana Pérez", "Consulta")));
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, "Vacaciones")))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("AGENDA_BLOCK_HAS_BOOKINGS");
                    assertThat(exception.getDetails()).containsOnlyKeys(bookingId.toString());
                    assertThat(exception.getDetails().get(bookingId.toString()))
                            .contains("Ana Pérez")
                            .contains("Consulta")
                            .contains(DAY.format(bookingStart))
                            .contains(HOUR.format(bookingStart))
                            .contains(HOUR.format(bookingEnd));
                });

        // El bloqueo NO se guarda.
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloqueo con reservas confirmadas activas.
    // Con 3 reservas confirmadas, el detalle trae una entrada por cita.
    // ------------------------------------------------------------------
    @Test
    void detallaLasTresReservasConfirmadasQueImpidenElBloqueo() {
        // Arrange
        UUID primera = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        UUID tercera = UUID.randomUUID();
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of(
                new ConfirmedBookingsLookup.AffectedBooking(
                        primera, inicio.plusHours(1), inicio.plusHours(2), "Ana", "Consulta"),
                new ConfirmedBookingsLookup.AffectedBooking(
                        segunda, inicio.plusHours(3), inicio.plusHours(4), "Luis", "Terapia"),
                new ConfirmedBookingsLookup.AffectedBooking(
                        tercera, inicio.plusHours(5), inicio.plusHours(6), "Sara", "Asesoría")));
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("AGENDA_BLOCK_HAS_BOOKINGS");
                    assertThat(exception.getDetails()).hasSize(3);
                    assertThat(exception.getDetails().keySet())
                            .containsExactlyInAnyOrder(
                                    primera.toString(), segunda.toString(), tercera.toString());
                    assertThat(exception.getDetails().get(primera.toString())).contains("Ana");
                    assertThat(exception.getDetails().get(segunda.toString())).contains("Luis");
                    assertThat(exception.getDetails().get(tercera.toString())).contains("Sara");
                });

        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloqueo con reservas confirmadas activas.
    // El conflicto con reservas se evalua antes que el solapamiento.
    // ------------------------------------------------------------------
    @Test
    void priorizaElConflictoConReservasSobreElSolapamientoDeBloqueos() {
        // Arrange
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of(
                new ConfirmedBookingsLookup.AffectedBooking(
                        UUID.randomUUID(), inicio, fin, "Cliente", "Servicio")));

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("AGENDA_BLOCK_HAS_BOOKINGS"));

        // Assert
        verify(repository, never()).findOverlapping(professionalId, inicio, fin);
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Se rechazan los bloqueos que se cruzan con otro bloqueo.
    // ------------------------------------------------------------------
    @Test
    void rechazaElBloqueoCuandoSeSolapaConOtroBloqueo() {
        // Arrange
        AgendaBlock existing = block(inicio, fin);
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of(existing));

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("AGENDA_BLOCK_OVERLAP");
                    assertThat(exception.getDetails()).containsOnlyKeys(existing.getId().toString());
                    assertThat(exception.getDetails().get(existing.getId().toString()))
                            .contains(HOUR.format(inicio))
                            .contains(HOUR.format(fin));
                });

        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // El detalle del conflicto de solapamiento lista cada bloqueo cruzado.
    // ------------------------------------------------------------------
    @Test
    void detallaTodosLosBloqueosSolapados() {
        // Arrange
        AgendaBlock first = block(inicio, fin);
        AgendaBlock second = block(inicio.plusHours(3), inicio.plusHours(7));
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of(first, second));

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception ->
                        assertThat(exception.getDetails())
                                .containsOnlyKeys(first.getId().toString(), second.getId().toString()));

        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: un rango invertido se rechaza con detalle en endTime.
    // ------------------------------------------------------------------
    @Test
    void rechazaElRangoInvertidoConDetalleEnEndTime() {
        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(fin, inicio, null)))
                .isInstanceOfSatisfying(ScheduleValidationException.class, exception ->
                        assertThat(exception.getDetails()).containsKey("endTime"));

        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: un rango con fin igual al inicio se rechaza.
    // ------------------------------------------------------------------
    @Test
    void rechazaElRangoConFinIgualAlInicio() {
        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, inicio, null)))
                .isInstanceOfSatisfying(ScheduleValidationException.class, exception ->
                        assertThat(exception.getDetails()).containsKey("endTime"));

        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: no se puede bloquear un rango ya pasado.
    // ------------------------------------------------------------------
    @Test
    void rechazaElBloqueoDeUnRangoEnElPasado() {
        // Arrange
        LocalDateTime pasado = LocalDateTime.now().minusDays(1);
        LocalDateTime finPasado = LocalDateTime.now().minusHours(1);

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(pasado, finPasado, null)))
                .isInstanceOfSatisfying(ScheduleValidationException.class, exception ->
                        assertThat(exception.getDetails()).containsKey("startTime"));

        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: rango invertido y en el pasado: se reportan ambos detalles.
    // ------------------------------------------------------------------
    @Test
    void reportaLosDosErroresCuandoElRangoEstaInvertidoYEnElPasado() {
        // Arrange
        LocalDateTime pasado = LocalDateTime.now().minusDays(1);

        // Act
        assertThatThrownBy(() -> service.create(
                professionalId, request(pasado, pasado.minusHours(1), null)))
                .isInstanceOfSatisfying(ScheduleValidationException.class, exception ->
                        assertThat(exception.getDetails())
                                .containsKeys("startTime", "endTime"));

        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Listar devuelve solo los bloqueos vigentes o futuros.
    // ------------------------------------------------------------------
    @Test
    void listaLosBloqueosVigentesOrdenadosPorInicio() {
        // Arrange
        AgendaBlock first = block(inicio, fin);
        AgendaBlock second = block(inicio.plusDays(1), inicio.plusDays(1).plusHours(4));
        when(repository.findByProfessionalIdAndEndTimeAfterOrderByStartTimeAsc(
                eq(professionalId), any(LocalDateTime.class))).thenReturn(List.of(first, second));

        // Act
        List<AgendaBlockResponse> response = service.findUpcoming(professionalId);

        // Assert
        assertThat(response).hasSize(2);
        assertThat(response.get(0).startTime()).isEqualTo(inicio);
        assertThat(response.get(1).startTime()).isEqualTo(inicio.plusDays(1));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Si no hay bloqueos, la lista vacia.
    // ------------------------------------------------------------------
    @Test
    void devuelveListaVaciaCuandoNoHayBloqueos() {
        // Arrange
        when(repository.findByProfessionalIdAndEndTimeAfterOrderByStartTimeAsc(
                eq(professionalId), any(LocalDateTime.class))).thenReturn(List.of());

        // Act
        List<AgendaBlockResponse> response = service.findUpcoming(professionalId);

        // Assert
        assertThat(response).isEmpty();
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // La consulta de bloqueos usa "ahora" como referencia.
    // ------------------------------------------------------------------
    @Test
    void filtraLosBloqueosTerminadosUsandoElMomentoActual() {
        // Arrange
        when(repository.findByProfessionalIdAndEndTimeAfterOrderByStartTimeAsc(
                eq(professionalId), any(LocalDateTime.class))).thenReturn(List.of());

        // Act
        service.findUpcoming(professionalId);

        // Assert: el instante de corte capturado es el momento actual.
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findByProfessionalIdAndEndTimeAfterOrderByStartTimeAsc(
                eq(professionalId), captor.capture());
        assertThat(captor.getValue()).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Borrar un bloqueo propio lo elimina.
    // ------------------------------------------------------------------
    @Test
    void eliminaElBloqueoCuandoPerteneceAlProfesional() {
        // Arrange
        UUID blockId = UUID.randomUUID();
        AgendaBlock block = block(inicio, fin);
        ReflectionTestUtils.setField(block, "id", blockId);
        when(repository.findByIdAndProfessionalId(blockId, professionalId)).thenReturn(Optional.of(block));

        // Act
        service.delete(professionalId, blockId);

        // Assert
        verify(repository).delete(block);
    }

    // ------------------------------------------------------------------
    // QA/Exception: borrar un bloqueo inexistente responde not found.
    // ------------------------------------------------------------------
    @Test
    void devuelveNotFoundAlBorrarUnBloqueoInexistente() {
        // Arrange
        UUID blockId = UUID.randomUUID();
        when(repository.findByIdAndProfessionalId(blockId, professionalId)).thenReturn(Optional.empty());

        // Act
        assertThatThrownBy(() -> service.delete(professionalId, blockId))
                .isInstanceOfSatisfying(AgendaBlockNotFoundException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("AGENDA_BLOCK_NOT_FOUND");
                    assertThat(exception.getDetails()).containsEntry("bloqueoId", blockId.toString());
                });

        // Assert
        verify(repository, never()).delete(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: borrar un bloqueo de otro profesional no lo elimina.
    // ------------------------------------------------------------------
    @Test
    void noEliminaUnBloqueoDeOtroProfesional() {
        // Arrange
        UUID blockId = UUID.randomUUID();
        when(repository.findByIdAndProfessionalId(blockId, professionalId)).thenReturn(Optional.empty());

        // Act
        assertThatThrownBy(() -> service.delete(professionalId, blockId))
                .isInstanceOf(AgendaBlockNotFoundException.class);

        // Assert
        verify(repository, never()).delete(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // El motivo se recorta y los espacios sobrantes se eliminan.
    // ------------------------------------------------------------------
    @Test
    void recortaElMotivoDelBloqueo() {
        // Arrange
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.saveAndFlush(any(AgendaBlock.class))).thenAnswer(invocation -> {
            AgendaBlock saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        // Act
        AgendaBlockResponse response =
                service.create(professionalId, request(inicio, fin, "   Vacaciones   "));

        // Assert
        assertThat(response.reason()).isEqualTo("Vacaciones");
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Un motivo vacio o nulo se guarda como null.
    // ------------------------------------------------------------------
    @Test
    void guardaElMotivoComoNullCuandoVieneVacio() {
        // Arrange
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.saveAndFlush(any(AgendaBlock.class))).thenAnswer(invocation -> {
            AgendaBlock saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        // Act
        AgendaBlockResponse response = service.create(professionalId, request(inicio, fin, "   "));

        // Assert
        assertThat(response.reason()).isNull();
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloqueo con reservas confirmadas activas.
    // Una reserva sin nombre de cliente se describe como "Cliente".
    // ------------------------------------------------------------------
    @Test
    void describeLaCitaAfectadaSinNombreDeClienteComoCliente() {
        // Arrange
        UUID bookingId = UUID.randomUUID();
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of(
                new ConfirmedBookingsLookup.AffectedBooking(
                        bookingId, inicio, fin, null, null)));
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception ->
                        assertThat(exception.getDetails().get(bookingId.toString()))
                                .contains("Cliente")
                                .contains("Servicio"));

        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloqueo con reservas confirmadas activas.
    // Un rango que cruza medianoche muestra ambos dias.
    // ------------------------------------------------------------------
    @Test
    void describeElRangoQueCruzaMedianocheConLosDosDias() {
        // Arrange
        LocalDateTime inicioNoche = inicio.withHour(22);
        LocalDateTime finMadrugada = inicioNoche.plusHours(8);
        UUID bookingId = UUID.randomUUID();
        when(confirmedBookings.findConfirmed(professionalId, inicioNoche, finMadrugada)).thenReturn(List.of(
                new ConfirmedBookingsLookup.AffectedBooking(
                        bookingId, inicioNoche, finMadrugada, "Ana", "Consulta")));
        when(repository.findOverlapping(professionalId, inicioNoche, finMadrugada)).thenReturn(List.of());

        // Act
        assertThatThrownBy(() -> service.create(
                professionalId, request(inicioNoche, finMadrugada, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception ->
                        assertThat(exception.getDetails().get(bookingId.toString()))
                                .contains(DAY.format(inicioNoche))
                                .contains(DAY.format(finMadrugada))
                                .contains(HOUR.format(finMadrugada)));

        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // El detalle del conflicto de solapamiento usa el mismo formato de fecha.
    // ------------------------------------------------------------------
    @Test
    void describeElBloqueoSolapadoConElMismoFormatoDeFecha() {
        // Arrange
        LocalDateTime inicioNoche = inicio.withHour(22);
        LocalDateTime finMadrugada = inicioNoche.plusHours(8);
        AgendaBlock existing = block(inicioNoche, finMadrugada);
        when(confirmedBookings.findConfirmed(professionalId, inicioNoche, finMadrugada)).thenReturn(List.of());
        when(repository.findOverlapping(professionalId, inicioNoche, finMadrugada)).thenReturn(List.of(existing));

        // Act
        assertThatThrownBy(() -> service.create(
                professionalId, request(inicioNoche, finMadrugada, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception ->
                        assertThat(exception.getDetails().get(existing.getId().toString()))
                                .contains(DAY.format(inicioNoche))
                                .contains(DAY.format(finMadrugada))
                                .contains(HOUR.format(inicioNoche))
                                .contains(HOUR.format(finMadrugada)));

        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: la validacion del rango se ejecuta antes de tocar la base.
    // ------------------------------------------------------------------
    @Test
    void validaElRangoAntesDeConsultarLasReservasConfirmadas() {
        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(fin, inicio, null)))
                .isInstanceOf(ScheduleValidationException.class);

        // Assert
        verify(confirmedBookings, never()).findConfirmed(any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloqueo con reservas confirmadas activas.
    // El detalle de la cita afectada une fecha, cliente y servicio.
    // ------------------------------------------------------------------
    @Test
    void uneFechaClienteYServicioEnElDetalleDeLaCitaAfectada() {
        // Arrange
        UUID bookingId = UUID.randomUUID();
        LocalDateTime bookingStart = inicio.plusHours(1);
        LocalDateTime bookingEnd = inicio.plusHours(2);
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of(
                new ConfirmedBookingsLookup.AffectedBooking(
                        bookingId, bookingStart, bookingEnd, "Ana Pérez", "Consulta")));
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception -> {
                    String detalle = exception.getDetails().get(bookingId.toString());
                    assertThat(detalle)
                            .isEqualTo(DAY.format(bookingStart) + " " + HOUR.format(bookingStart)
                                    + "–" + HOUR.format(bookingEnd)
                                    + " · Ana Pérez · Consulta");
                });

        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // El rango de la consulta a las reservas es el del bloqueo.
    // ------------------------------------------------------------------
    @Test
    void consultaLasReservasConfirmadasEnElRangoDelBloqueo() {
        // Arrange
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.saveAndFlush(any(AgendaBlock.class))).thenAnswer(invocation -> {
            AgendaBlock saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        // Act
        service.create(professionalId, request(inicio, fin, null));

        // Assert
        verify(confirmedBookings).findConfirmed(professionalId, inicio, fin);
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Un bloqueo ya guardado se devuelve mapeado a respuesta.
    // ------------------------------------------------------------------
    @Test
    void mapeaElBloqueoGuardadoALaRespuesta() {
        // Arrange
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.saveAndFlush(any(AgendaBlock.class))).thenAnswer(invocation -> {
            AgendaBlock saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        // Act
        AgendaBlockResponse response = service.create(professionalId, request(inicio, fin, "Imprevisto"));

        // Assert
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.startTime()).isEqualTo(inicio);
        assertThat(response.endTime()).isEqualTo(fin);
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloqueo con reservas confirmadas activas.
    // El mensaje del conflicto con reservas explica la accion a tomar.
    // ------------------------------------------------------------------
    @Test
    void elConflictoConReservasExplicaQueHayQueCancelarOReprogramar() {
        // Arrange
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of(
                new ConfirmedBookingsLookup.AffectedBooking(
                        UUID.randomUUID(), inicio, fin, "Ana", "Consulta")));
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception ->
                        assertThat(exception.getMessage())
                                .contains("reservas confirmadas")
                                .contains("Canc"));
        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // El mensaje del solapamiento menciona el bloqueo existente.
    // ------------------------------------------------------------------
    @Test
    void elConflictoDeSolapamientoMencionaElBloqueoExistente() {
        // Arrange
        AgendaBlock existing = block(inicio, fin);
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of(existing));

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception ->
                        assertThat(exception.getMessage()).contains("bloqueo"));
        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: el detalle del rango invertido explica la regla.
    // ------------------------------------------------------------------
    @Test
    void elDetalleDelRangoInvertidoExplicaLaRegla() {
        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(fin, inicio, null)))
                .isInstanceOfSatisfying(ScheduleValidationException.class, exception ->
                        assertThat(exception.getDetails().get("endTime"))
                                .contains("posterior"));
        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: el detalle del rango en el pasado explica la regla.
    // ------------------------------------------------------------------
    @Test
    void elDetalleDelRangoEnElPasadoExplicaLaRegla() {
        // Arrange
        LocalDateTime pasado = LocalDateTime.now().minusDays(2);
        LocalDateTime finPasado = LocalDateTime.now().minusDays(1);

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(pasado, finPasado, null)))
                .isInstanceOfSatisfying(ScheduleValidationException.class, exception ->
                        assertThat(exception.getDetails().get("startTime")).contains("pasó"));
        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: el mensaje de validacion es el estandar de horario.
    // ------------------------------------------------------------------
    @Test
    void elMensajeDeValidacionEsElEstandarDeHorario() {
        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(fin, inicio, null)))
                .isInstanceOfSatisfying(ScheduleValidationException.class, exception ->
                        assertThat(exception.getMessage()).isEqualTo("El horario contiene datos inválidos"));
        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: el detalle del bloqueo inexistente lleva el id del bloqueo.
    // ------------------------------------------------------------------
    @Test
    void elDetalleDelBloqueoInexistenteLlevaElIdDelBloqueo() {
        // Arrange
        UUID blockId = UUID.randomUUID();
        when(repository.findByIdAndProfessionalId(blockId, professionalId)).thenReturn(Optional.empty());

        // Act
        assertThatThrownBy(() -> service.delete(professionalId, blockId))
                .isInstanceOfSatisfying(AgendaBlockNotFoundException.class, exception ->
                        assertThat(exception.getMessage()).isEqualTo("El bloqueo no existe"));
        // Assert
        verify(repository, never()).delete(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloqueo con reservas confirmadas activas.
    // La excepcion de conflicto expone sus detalles.
    // ------------------------------------------------------------------
    @Test
    void laExcepcionDeConflictoExponeSusDetalles() {
        // Arrange
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of(
                new ConfirmedBookingsLookup.AffectedBooking(
                        UUID.randomUUID(), inicio, fin, "Ana", "Consulta")));
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception ->
                        assertThat(exception.getDetails()).isNotEmpty().hasSize(1));
        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // La excepcion de solapamiento usa su propio codigo de error.
    // ------------------------------------------------------------------
    @Test
    void laExcepcionDeSolapamientoUsaSuPropioCodigoDeError() {
        // Arrange
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of(block(inicio, fin)));

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("AGENDA_BLOCK_OVERLAP"));
        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloqueo con reservas confirmadas activas.
    // La excepcion de conflicto de reservas usa su propio codigo de error.
    // ------------------------------------------------------------------
    @Test
    void laExcepcionDeConflictoDeReservasUsaSuPropioCodigoDeError() {
        // Arrange
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of(
                new ConfirmedBookingsLookup.AffectedBooking(
                        UUID.randomUUID(), inicio, fin, "Ana", "Consulta")));
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("AGENDA_BLOCK_HAS_BOOKINGS"));
        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: la restriccion de exclusion de la base se traduce a conflicto de solapamiento.
    // ------------------------------------------------------------------
    @Test
    void traduceLaViolacionDeIntegridadPorSolapamientoAConflicto() {
        // Arrange
        AgendaBlock existing = block(inicio, fin);
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of());
        // La primera consulta de solapamientos va vacia; tras la violacion de integridad, la
        // segunda encuentra el bloqueo que gano la carrera.
        when(repository.findOverlapping(professionalId, inicio, fin))
                .thenReturn(List.of())
                .thenReturn(List.of(existing));
        when(repository.saveAndFlush(any(AgendaBlock.class)))
                .thenThrow(new DataIntegrityViolationException("bloqueo_agenda_sin_solapamiento"));

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("AGENDA_BLOCK_OVERLAP");
                    assertThat(exception.getDetails()).containsOnlyKeys(existing.getId().toString());
                });

        // Assert
        verify(repository).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: si la violacion de integridad no encuentra solapamiento, el conflicto sale vacio.
    //
    // Comportamiento conocido, no deseable: cuando la base rechaza el INSERT por solapamiento pero
    // la segunda consulta no encuentra el bloqueo conflictivo (por ejemplo, si otro profesional
    // lo elimino entre medias), la API responde 409 AGENDA_BLOCK_OVERLAP sin ningun detalle que
    // indique que bloqueos estan en conflicto. Se documenta como brecha conocida: el cliente no
    // puede saber que debe ajustar.
    // ------------------------------------------------------------------
    @Test
    void elConflictoTrasViolacionDeIntegridadSaleSinDetalles() {
        // Arrange
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());
        when(repository.saveAndFlush(any(AgendaBlock.class)))
                .thenThrow(new DataIntegrityViolationException("bloqueo_agenda_sin_solapamiento"));

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception ->
                        assertThat(exception.getDetails()).isEmpty());

        // Assert
        verify(repository).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: borrar un bloqueo llama al repositorio con la entidad leida.
    // ------------------------------------------------------------------
    @Test
    void borraLaEntidadLeidaDelRepositorio() {
        // Arrange
        UUID blockId = UUID.randomUUID();
        AgendaBlock block = block(inicio, fin);
        ReflectionTestUtils.setField(block, "id", blockId);
        when(repository.findByIdAndProfessionalId(blockId, professionalId)).thenReturn(Optional.of(block));

        // Act
        service.delete(professionalId, blockId);

        // Assert
        verify(repository).findByIdAndProfessionalId(blockId, professionalId);
        verify(repository).delete(block);
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloqueo con reservas confirmadas activas.
    // El detalle de las citas afectadas conserva el orden de las reservas.
    // ------------------------------------------------------------------
    @Test
    void conservaElOrdenDeLasCitasAfectadasEnElDetalle() {
        // Arrange
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        when(confirmedBookings.findConfirmed(professionalId, inicio, fin)).thenReturn(List.of(
                new ConfirmedBookingsLookup.AffectedBooking(
                        primero, inicio, fin, "Ana", "Consulta"),
                new ConfirmedBookingsLookup.AffectedBooking(
                        segundo, inicio, fin, "Luis", "Terapia")));
        when(repository.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());

        // Act
        assertThatThrownBy(() -> service.create(professionalId, request(inicio, fin, null)))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception ->
                        assertThat(exception.getDetails().keySet())
                                .containsExactly(primero.toString(), segundo.toString()));

        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: listar no escribe nada en la base.
    // ------------------------------------------------------------------
    @Test
    void listarNoEscribeEnLaBase() {
        // Arrange
        when(repository.findByProfessionalIdAndEndTimeAfterOrderByStartTimeAsc(
                eq(professionalId), any(LocalDateTime.class))).thenReturn(List.of(block(inicio, fin)));

        // Act
        service.findUpcoming(professionalId);

        // Assert
        verify(repository, never()).saveAndFlush(any(AgendaBlock.class));
        verify(repository, never()).delete(any(AgendaBlock.class));
    }

    // ------------------------------------------------------------------
    // QA/Exception: borrar no consulta las reservas confirmadas.
    // ------------------------------------------------------------------
    @Test
    void borrarNoConsultaLasReservasConfirmadas() {
        // Arrange
        UUID blockId = UUID.randomUUID();
        AgendaBlock block = block(inicio, fin);
        ReflectionTestUtils.setField(block, "id", blockId);
        when(repository.findByIdAndProfessionalId(blockId, professionalId)).thenReturn(Optional.of(block));

        // Act
        service.delete(professionalId, blockId);

        // Assert
        verify(confirmedBookings, never()).findConfirmed(any(), any(), any());
    }

    private AgendaBlockRequest request(LocalDateTime start, LocalDateTime end, String reason) {
        return new AgendaBlockRequest(start, end, reason);
    }

    private AgendaBlock block(LocalDateTime start, LocalDateTime end) {
        AgendaBlock block = new AgendaBlock(professionalId, start, end, "Motivo");
        ReflectionTestUtils.setField(block, "id", UUID.randomUUID());
        return block;
    }
}
