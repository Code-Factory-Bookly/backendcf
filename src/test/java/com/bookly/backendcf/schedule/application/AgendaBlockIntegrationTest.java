package com.bookly.backendcf.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookly.backendcf.availability.application.AvailabilityService;
import com.bookly.backendcf.availability.presentation.dto.AvailabilityResponse;
import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.application.service.CreateBookingService;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.schedule.presentation.dto.AgendaBlockRequest;
import com.bookly.backendcf.schedule.presentation.dto.AgendaBlockResponse;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-05 contra PostgreSQL real: bloqueos de agenda de punta a punta (servicio, adaptadores,
 * repositorios JPA y migraciones Flyway).
 *
 * <p>Referencia a los criterios de aceptacion (Gherkin, HU-05 refinada Sprint 2):
 * <ul>
 *   <li>Escenario "Bloquear una franja sin reservas previas".</li>
 *   <li>Escenario "Intento de bloquear rango con reservas confirmadas activas".</li>
 * </ul>
 *
 * <p>Reglas y errores: {@code docs/HU-05-bloqueo-agenda.md}.
 */
@Tag("integration")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/bookly_test",
        "spring.datasource.username=bookly_test",
        "spring.datasource.password=bookly_test_local_2026",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "security.jwt.secret=BooklyIntegrationTestOnlySecretWithMoreThanSixtyFourCharacters123456789"
})
class AgendaBlockIntegrationTest {

    @Autowired
    private AgendaBlockService blockService;

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private CreateBookingService createBookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID professionalId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final String suffix = UUID.randomUUID().toString();
    private final OffsetDateTime now = OffsetDateTime.now();

    /** Ids de profesionales auxiliares (con su usuario en app_user) que se limpian en el @AfterEach. */
    private final List<UUID> idsAuxiliares = new ArrayList<>();

    /** Ids de servicios auxiliares que se limpian en el @AfterEach. */
    private final List<UUID> serviciosAuxiliares = new ArrayList<>();

    /** El lunes siguiente: siempre futuro, para que el servicio acepte el rango. */
    private final LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() {
        insertUser(professionalId, "professional-" + suffix + "@example.test", "PROFESSIONAL",
                "Profesional HU05");
        insertUser(customerId, "customer-" + suffix + "@example.test", "CUSTOMER", "Cliente HU05");
        jdbc.update("INSERT INTO profesionales (id, rol, especialidad, created_at, updated_at) "
                        + "VALUES (?, 'PROFESSIONAL', 'General', ?, ?)",
                professionalId, now, now);
        jdbc.update("INSERT INTO servicios (id, nombre, categoria, duracion_minutos, precio, estado, "
                        + "created_at, updated_at) VALUES (?, ?, 'General', 60, ?, 'ACTIVO', ?, ?)",
                serviceId, "Servicio HU05 " + suffix, new BigDecimal("100.00"), now, now);
        jdbc.update("INSERT INTO horario_semanal (id, profesional_id, dia_semana, hora_inicio, hora_fin, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), professionalId, "MONDAY", LocalTime.of(8, 0), LocalTime.of(18, 0),
                now, now);
    }

    @AfterEach
    void cleanUp() {
        // Orden obligatorio por las claves foraneas:
        // bloqueo_agenda -> horario_semanal -> profesionales -> app_user.
        jdbc.update("DELETE FROM bloqueo_agenda WHERE profesional_id = ?", professionalId);
        jdbc.update("DELETE FROM bookings WHERE professional_id = ?", professionalId);
        jdbc.update("DELETE FROM horario_semanal WHERE profesional_id = ?", professionalId);
        jdbc.update("DELETE FROM profesionales WHERE id = ?", professionalId);
        jdbc.update("DELETE FROM servicios WHERE id = ?", serviceId);

        // Los profesionales auxiliares que creo alguna prueba (por ejemplo, el "otro
        // profesional") se limpian tambien, en el mismo orden. Su id es el mismo que el de
        // su usuario en app_user (profesionales.id referencia app_user.id).
        for (UUID idAuxiliar : idsAuxiliares) {
            jdbc.update("DELETE FROM bloqueo_agenda WHERE profesional_id = ?", idAuxiliar);
            jdbc.update("DELETE FROM bookings WHERE professional_id = ?", idAuxiliar);
            jdbc.update("DELETE FROM horario_semanal WHERE profesional_id = ?", idAuxiliar);
            jdbc.update("DELETE FROM profesional_servicio WHERE profesional_id = ?", idAuxiliar);
            jdbc.update("DELETE FROM profesionales WHERE id = ?", idAuxiliar);
            // Los bloqueos de agenda no se auditan, asi que el usuario auxiliar no tiene filas
            // en audit_log y se puede borrar.
            jdbc.update("DELETE FROM app_user WHERE id = ?", idAuxiliar);
        }
        idsAuxiliares.clear();

        for (UUID idServicioAuxiliar : serviciosAuxiliares) {
            jdbc.update("DELETE FROM profesional_servicio WHERE servicio_id = ?", idServicioAuxiliar);
            jdbc.update("DELETE FROM servicios WHERE id = ?", idServicioAuxiliar);
        }
        serviciosAuxiliares.clear();

        // El usuario PROFESIONAL si se borra: los bloqueos de agenda no se auditan
        // (HU-05 "Sin auditoria"), asi que no tiene filas en audit_log.
        jdbc.update("DELETE FROM app_user WHERE id = ?", professionalId);

        // El cliente CON reservas no se borra: audit_log es append-only (V40) y conserva la FK
        // fk_audit_log_actor hacia app_user, ademas el trigger trg_audit_log_immutable impide
        // borrar sus filas de auditoria. Si el cliente NO tiene reservas, si se puede borrar.
        Integer reservasDelCliente = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE actor_user_id = ?", Integer.class, customerId);
        if (reservasDelCliente != null && reservasDelCliente == 0) {
            jdbc.update("DELETE FROM app_user WHERE id = ?", customerId);
        }
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // ------------------------------------------------------------------
    @Test
    void bloqueaUnRangoYLaDisponibilidadDejaDeOfrecerlo() {
        // Arrange
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);

        // Act
        AgendaBlockResponse response = blockService.create(
                professionalId, new AgendaBlockRequest(inicio, fin, "Vacaciones"));

        // Assert
        assertThat(response.id()).isNotNull();
        assertThat(response.professionalId()).isEqualTo(professionalId);
        assertThat(response.startTime()).isEqualTo(inicio);
        assertThat(response.endTime()).isEqualTo(fin);

        Long bloqueos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bloqueo_agenda WHERE profesional_id = ?",
                Long.class, professionalId);
        assertThat(bloqueos).isEqualTo(1L);

        // La disponibilidad ya no ofrece las horas bloqueadas.
        AvailabilityResponse disponibilidad = availabilityService.find(
                professionalId, serviceId, monday, monday);
        assertThat(disponibilidad.days().get(0).slots())
                .extracting(AvailabilityResponse.Block::startTime)
                .doesNotContain(inicio, inicio.plusHours(1), inicio.plusHours(2));
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Un bloqueo contiguo al anterior se acepta (el rango es [inicio, fin)).
    // ------------------------------------------------------------------
    @Test
    void aceptaUnBloqueoContiguoAlAnterior() {
        // Arrange
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);
        LocalDateTime inicioContiguo = fin;
        LocalDateTime finContiguo = monday.atTime(15, 0);
        blockService.create(professionalId, new AgendaBlockRequest(inicio, fin, "Vacaciones"));

        // Act
        AgendaBlockResponse response = blockService.create(
                professionalId, new AgendaBlockRequest(inicioContiguo, finContiguo, "Imprevisto"));

        // Assert
        assertThat(response.id()).isNotNull();
        Long bloqueos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bloqueo_agenda WHERE profesional_id = ?",
                Long.class, professionalId);
        assertThat(bloqueos).isEqualTo(2L);
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // El bloqueo queda persistido y se puede listar.
    // ------------------------------------------------------------------
    @Test
    void persisteElBloqueoYLoDevuelveEnLaLista() {
        // Arrange
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);

        // Act
        AgendaBlockResponse creado = blockService.create(
                professionalId, new AgendaBlockRequest(inicio, fin, "Vacaciones"));

        // Assert
        var listados = blockService.findUpcoming(professionalId);
        assertThat(listados).hasSize(1);
        assertThat(listados.get(0).id()).isEqualTo(creado.id());
        assertThat(listados.get(0).reason()).isEqualTo("Vacaciones");
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // El bloqueo desaparece de la disponibilidad al borrarlo.
    // ------------------------------------------------------------------
    @Test
    void alBorrarElBloqueoLaDisponibilidadVuelveOfrecerLasHoras() {
        // Arrange
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);
        AgendaBlockResponse creado = blockService.create(
                professionalId, new AgendaBlockRequest(inicio, fin, "Vacaciones"));
        assertThat(availabilityService.find(professionalId, serviceId, monday, monday)
                .days().get(0).slots())
                .extracting(AvailabilityResponse.Block::startTime)
                .doesNotContain(inicio);

        // Act
        blockService.delete(professionalId, creado.id());

        // Assert
        Long bloqueos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bloqueo_agenda WHERE profesional_id = ?",
                Long.class, professionalId);
        assertThat(bloqueos).isZero();
        assertThat(availabilityService.find(professionalId, serviceId, monday, monday)
                .days().get(0).slots())
                .extracting(AvailabilityResponse.Block::startTime)
                .contains(inicio);
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Intento de bloquear rango con reservas confirmadas activas.
    // Una reserva confirmada en el rango impide el bloqueo (409 con las citas).
    // ------------------------------------------------------------------
    @Test
    void rechazaElBloqueoCuandoHayUnaReservaConfirmadaEnElRango() {
        // Arrange
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);
        insertBooking(inicio, inicio.plusHours(1), "CONFIRMADA");

        // Act
        assertThatThrownBy(() -> blockService.create(
                professionalId, new AgendaBlockRequest(inicio, fin, "Vacaciones")))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("AGENDA_BLOCK_HAS_BOOKINGS");
                    assertThat(exception.getDetails()).hasSize(1);
                    assertThat(exception.getDetails().values().iterator().next())
                            .contains("Cliente HU05")
                            .contains("Servicio HU05 " + suffix);
                });

        // Assert: no se creo el bloqueo.
        Long bloqueos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bloqueo_agenda WHERE profesional_id = ?",
                Long.class, professionalId);
        assertThat(bloqueos).isZero();
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Intento de bloquear rango con reservas confirmadas activas.
    // Con 3 reservas confirmadas, el detalle trae una entrada por cita.
    // ------------------------------------------------------------------
    @Test
    void rechazaElBloqueoYDetallaLasTresReservasConfirmadas() {
        // Arrange
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);
        insertBooking(inicio, inicio.plusHours(1), "CONFIRMADA");
        insertBooking(inicio.plusMinutes(90), inicio.plusMinutes(150), "CONFIRMADA");
        insertBooking(inicio.plusMinutes(160), inicio.plusMinutes(220), "CONFIRMADA");

        // Act
        assertThatThrownBy(() -> blockService.create(
                professionalId, new AgendaBlockRequest(inicio, fin, "Vacaciones")))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("AGENDA_BLOCK_HAS_BOOKINGS");
                    assertThat(exception.getDetails()).hasSize(3);
                    assertThat(exception.getDetails().values())
                            .allSatisfy(detalle ->
                                    assertThat(detalle).contains("Cliente HU05").contains("Servicio HU05"));
                });

        // Assert: no se creo el bloqueo.
        Long bloqueos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bloqueo_agenda WHERE profesional_id = ?",
                Long.class, professionalId);
        assertThat(bloqueos).isZero();
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Una reserva cancelada en el rango NO impide el bloqueo.
    // ------------------------------------------------------------------
    @Test
    void aceptaElBloqueoCuandoLaReservaDelRangoEstaCancelada() {
        // Arrange
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);
        insertBooking(inicio, inicio.plusHours(1), "CANCELADA");

        // Act
        AgendaBlockResponse response = blockService.create(
                professionalId, new AgendaBlockRequest(inicio, fin, "Vacaciones"));

        // Assert
        assertThat(response.id()).isNotNull();
        Long bloqueos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bloqueo_agenda WHERE profesional_id = ?",
                Long.class, professionalId);
        assertThat(bloqueos).isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // QA/Exception: el servicio rechaza el solapamiento antes de consultar la base.
    // ------------------------------------------------------------------
    @Test
    void elServicioRechazaElSolapamientoAntesDeConsultarLaBase() {
        // Arrange
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);
        blockService.create(professionalId, new AgendaBlockRequest(inicio, fin, "Vacaciones"));

        // Act: el servicio detecta el solapamiento antes de tocar la base.
        assertThatThrownBy(() -> blockService.create(
                professionalId, new AgendaBlockRequest(inicio.plusHours(1), fin.plusHours(1), "Otro")))
                .isInstanceOfSatisfying(AgendaBlockConflictException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("AGENDA_BLOCK_OVERLAP"));

        // Assert
        Long bloqueos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bloqueo_agenda WHERE profesional_id = ?",
                Long.class, professionalId);
        assertThat(bloqueos).isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // QA/Exception: la base impide dos bloqueos solapados del mismo profesional.
    // Se inserta directo por JDBC para ejercitar la restriccion
    // "bloqueo_agenda_sin_solapamiento EXCLUDE USING gist" de V50, que el servicio no
    // puede alcanzar porque siempre valida el solapamiento antes de insertar.
    // ------------------------------------------------------------------
    @Test
    void laBaseImpideDosBloqueosSolapadosDelMismoProfesional() {
        // Arrange
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);
        jdbc.update("INSERT INTO bloqueo_agenda (id, profesional_id, inicio, fin, motivo, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), professionalId, inicio, fin, "Vacaciones", now);

        // Act: el segundo bloqueo se solapa con el primero.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO bloqueo_agenda (id, profesional_id, inicio, fin, motivo, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), professionalId, inicio.plusHours(1), fin.plusHours(1), "Otro", now))
                // Assert: la restriccion de exclusion de V50 lo rechaza.
                .isInstanceOf(DataIntegrityViolationException.class);

        // Assert: solo queda el primer bloqueo.
        Long bloqueos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bloqueo_agenda WHERE profesional_id = ?",
                Long.class, professionalId);
        assertThat(bloqueos).isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // QA/Exception: la base impide rangos invertidos con el CHECK (fin > inicio) de V50.
    // ------------------------------------------------------------------
    @Test
    void laBaseImpideUnRangoInvertido() {
        // Arrange
        LocalDateTime inicio = monday.atTime(12, 0);
        LocalDateTime fin = monday.atTime(9, 0);

        // Act
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO bloqueo_agenda (id, profesional_id, inicio, fin, motivo, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), professionalId, inicio, fin, "Invertido", now))
                // Assert: la restriccion ck_bloqueo_agenda_rango lo rechaza.
                .isInstanceOf(DataIntegrityViolationException.class);

        // Assert: no se guardo nada.
        Long bloqueos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bloqueo_agenda WHERE profesional_id = ?",
                Long.class, professionalId);
        assertThat(bloqueos).isZero();
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // La reserva dentro de un rango bloqueado se rechaza con SLOT_BLOCKED.
    // ------------------------------------------------------------------
    @Test
    void rechazaLaReservaDentroDeUnRangoBloqueado() {
        // Arrange
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);
        blockService.create(professionalId, new AgendaBlockRequest(inicio, fin, "Vacaciones"));

        // Act
        assertThatThrownBy(() -> createBookingService.createBooking(
                customerId, professionalId, serviceId, inicio.plusMinutes(30), inicio.plusMinutes(90)))
                .isInstanceOfSatisfying(BookingConflictException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("SLOT_BLOCKED"));

        // Assert
        Long reservas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE professional_id = ?",
                Long.class, professionalId);
        assertThat(reservas).isZero();
    }

    // ------------------------------------------------------------------
    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Tras borrar el bloqueo, la reserva vuelve a aceptarse.
    // ------------------------------------------------------------------
    @Test
    void trasBorrarElBloqueoLaReservaVuelveAAceptarse() {
        // Arrange
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);
        AgendaBlockResponse creado = blockService.create(
                professionalId, new AgendaBlockRequest(inicio, fin, "Vacaciones"));
        assertThatThrownBy(() -> createBookingService.createBooking(
                customerId, professionalId, serviceId, inicio, inicio.plusHours(1)))
                .isInstanceOf(BookingConflictException.class);
        blockService.delete(professionalId, creado.id());

        // Act
        Booking reserva = createBookingService.createBooking(
                customerId, professionalId, serviceId, inicio, inicio.plusHours(1));

        // Assert
        assertThat(reserva.getId()).isNotNull();
        assertThat(reserva.getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
    }

    // ------------------------------------------------------------------
    // QA/Exception: el bloqueo de un profesional no afecta a otro profesional.
    // El otro profesional se limpia en el @AfterEach (idsAuxiliares), no en un try/finally.
    // ------------------------------------------------------------------
    @Test
    void elBloqueoDeUnProfesionalNoAfectaAOtro() {
        // Arrange
        UUID otroProfesionalId = UUID.randomUUID();
        UUID otroServicioId = UUID.randomUUID();
        idsAuxiliares.add(otroProfesionalId);
        serviciosAuxiliares.add(otroServicioId);
        insertUser(otroProfesionalId, "other-" + suffix + "@example.test", "PROFESSIONAL",
                "Otro Profesional HU05");
        jdbc.update("INSERT INTO profesionales (id, rol, especialidad, created_at, updated_at) "
                        + "VALUES (?, 'PROFESSIONAL', 'General', ?, ?)",
                otroProfesionalId, now, now);
        jdbc.update("INSERT INTO servicios (id, nombre, categoria, duracion_minutos, precio, estado, "
                        + "created_at, updated_at) VALUES (?, ?, 'General', 60, ?, 'ACTIVO', ?, ?)",
                otroServicioId, "Otro Servicio HU05 " + suffix, new BigDecimal("100.00"), now, now);
        jdbc.update("INSERT INTO horario_semanal (id, profesional_id, dia_semana, hora_inicio, "
                        + "hora_fin, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), otroProfesionalId, "MONDAY", LocalTime.of(8, 0), LocalTime.of(18, 0),
                now, now);
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);
        blockService.create(professionalId, new AgendaBlockRequest(inicio, fin, "Vacaciones"));

        // Act: el otro profesional si puede reservar en ese mismo rango.
        Booking reserva = createBookingService.createBooking(
                customerId, otroProfesionalId, otroServicioId, inicio, inicio.plusHours(1));

        // Assert
        assertThat(reserva.getId()).isNotNull();
        assertThat(reserva.getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
    }

    // ------------------------------------------------------------------
    // QA/Exception: el bloqueo terminado no aparece en la lista de bloqueos vigentes.
    // ------------------------------------------------------------------
    @Test
    void noListaLosBloqueosQueYaTerminaron() {
        // Arrange
        // Se inserta directo en la base porque el servicio no deja crear bloqueos en el pasado.
        jdbc.update("INSERT INTO bloqueo_agenda (id, profesional_id, inicio, fin, motivo, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), professionalId,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), "Pasado", now);

        // Act
        var listados = blockService.findUpcoming(professionalId);

        // Assert
        assertThat(listados).isEmpty();
    }

    // ------------------------------------------------------------------
    // QA/Exception: borrar un bloqueo de otro profesional no lo elimina.
    // ------------------------------------------------------------------
    @Test
    void noEliminaElBloqueoDeOtroProfesional() {
        // Arrange
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);
        AgendaBlockResponse creado = blockService.create(
                professionalId, new AgendaBlockRequest(inicio, fin, "Vacaciones"));
        UUID otroProfesionalId = UUID.randomUUID();

        // Act
        assertThatThrownBy(() -> blockService.delete(otroProfesionalId, creado.id()))
                .isInstanceOf(AgendaBlockNotFoundException.class);

        // Assert
        Long bloqueos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bloqueo_agenda WHERE profesional_id = ?",
                Long.class, professionalId);
        assertThat(bloqueos).isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // QA/Exception: la limpieza borra el usuario PROFESIONAL (los bloqueos de agenda no se
    // auditan) y deja al cliente con reservas (audit_log es append-only y conserva su FK).
    // ------------------------------------------------------------------
    @Test
    void laLimpiezaBorraElUsuarioProfesionalPeroConservaElClienteConReservas() {
        // Arrange: una reserva creada por el servicio genera la fila BOOKING_CREATED en audit_log.
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);
        createBookingService.createBooking(
                customerId, professionalId, serviceId, inicio, inicio.plusHours(1));

        // Act: se reproduce a mano la limpieza del @AfterEach.
        cleanUp();

        // Assert: el usuario del profesional ya no esta (no tiene filas en audit_log).
        Long usuariosProfesional = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE id = ?", Long.class, professionalId);
        assertThat(usuariosProfesional).isZero();

        // Assert: el cliente con reservas se conserva porque audit_log conserva su FK.
        Long usuariosCliente = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE id = ?", Long.class, customerId);
        assertThat(usuariosCliente).isEqualTo(1L);

        // Assert: tampoco queda nada del profesional en las tablas de negocio.
        Long profesionales = jdbc.queryForObject(
                "SELECT COUNT(*) FROM profesionales WHERE id = ?", Long.class, professionalId);
        assertThat(profesionales).isZero();
    }

    // ------------------------------------------------------------------
    // QA/Exception: la limpieza si borra al cliente cuando no tiene reservas.
    // ------------------------------------------------------------------
    @Test
    void laLimpiezaBorraElClienteCuandoNoTieneReservas() {
        // Arrange: este test no crea reservas, asi que el cliente no tiene filas en audit_log.
        LocalDateTime inicio = monday.atTime(9, 0);
        LocalDateTime fin = monday.atTime(12, 0);
        blockService.create(professionalId, new AgendaBlockRequest(inicio, fin, "Vacaciones"));

        // Act
        cleanUp();

        // Assert
        Long usuariosCliente = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE id = ?", Long.class, customerId);
        assertThat(usuariosCliente).isZero();
        Long usuariosProfesional = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE id = ?", Long.class, professionalId);
        assertThat(usuariosProfesional).isZero();
        Long bloqueos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bloqueo_agenda WHERE profesional_id = ?",
                Long.class, professionalId);
        assertThat(bloqueos).isZero();
    }

    private void insertUser(UUID id, String email, String role, String fullName) {
        jdbc.update("INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, "
                        + "created_at, updated_at) VALUES (?, ?, 'test-not-a-real-password', ?, ?, true, ?, ?)",
                id, email, fullName, role, now, now);
    }

    private void insertBooking(LocalDateTime start, LocalDateTime end, String status) {
        jdbc.update("INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, "
                        + "end_time, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), customerId, professionalId, serviceId, start, end, status);
    }
}
