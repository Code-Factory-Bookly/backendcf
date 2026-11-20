package com.bookly.backendcf.booking.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.availability.application.AvailabilityService;
import com.bookly.backendcf.availability.presentation.dto.AvailabilityResponse;
import com.bookly.backendcf.support.IntegrationTestSupport;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * S3-10 y S3-03 · Integridad de la agenda frente al estado {@code RESCHEDULED}.
 *
 * <p><b>S3-10 (MEDIA): una reserva {@code RESCHEDULED} puede solaparse con una CONFIRMADA.</b>
 * La restriccion de exclusion {@code booking_no_overlapping_confirmed} de la migracion
 * {@code V46} declara el predicado {@code WHERE (status = 'CONFIRMADA')}, y nada mas. Una reserva
 * reprogramada queda fuera de la restriccion, de modo que dos reservas pueden ocupar fisicamente
 * la misma franja: el profesional recibe dos citas para el mismo horario.
 *
 * <p><b>Decision de negocio asumida.</b> Este test asume que {@code RESCHEDULED} <b>ocupa
 * agenda</b>, es decir, que la reprogramacion deja la reserva en su franja original y crea otra
 * nueva. Es la lectura coherente con el resto de la plataforma: el estado {@code RESCHEDULED}
 * existe en el enum {@code BookingStatus}, se notifica como evento
 * ({@code NotificationEventType.BOOKING_RESCHEDULED}) y {@code NotificationService} lo trata como
 * un hecho consumado. Bajo esa lectura, {@code RESCHEDULED} debe comportarse igual que
 * {@code CONFIRMADA}: bloquear la agenda, salir de la disponibilidad y contarse en el reporte.
 *
 * <p>Si el negocio decidiera lo contrario (que reprogramar reescribe la reserva y deja la original
 * liberada), la predicado de la exclusion deberia ser el que cambie, no este test: en ese caso la
 * exclusionActual seria la correcta y el defecto S3-10 no existiria. El caso esta escrito para
 * dejar explicita la assumption, de modo que una decision de negocio contraria se detecte como un
 * cambio de criterio y no como un fallo de codigo.
 *
 * <p><b>S3-03 (ALTA): la misma definicion de "ocupado" en todos los modulos.</b> El reporte de
 * ocupacion cuenta {@code CONFIRMADA} y {@code COMPLETED}; la disponibilidad solo cuenta
 * {@code CONFIRMADA}. Una reserva atendida vuelve a ofrecerse como reservable. Este caso exige que
 * {@code RESCHEDULED} se excluya de la disponibilidad igual que {@code CONFIRMADA}.
 */
@Tag("integration")
@SpringBootTest
class BookingOccupancyStatusIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AvailabilityService availabilityService;

    private final UUID professionalId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    /** Mañana siguiente: siempre futuro, porque el servicio de disponibilidad rechaza el pasado. */
    private final LocalDate manana = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() {
        insertProfessional(professionalId, uniqueEmail("profesional"), "Profesional Estados");
        insertUser(customerId, uniqueEmail("cliente"), "CUSTOMER", "Cliente Estados");
        insertService(serviceId, "Servicio Estados " + suffix, 60);
        insertWeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
    }

    @AfterEach
    void cleanUp() {
        limpiarBaseDeDatos();
    }

    // ----------------------------------------------------------------------------------
    // S3-10: la base de datos debe rechazar dos reservas que ocupan la misma franja
    // cuando una de ellas esta RESCHEDULED.
    // ----------------------------------------------------------------------------------

    @Test
    void laBaseRechazaUnaReservaConfirmadaSobreUnaFranjaReservadaPorUnaReservaReprogramada() {
        // Arrange: se siembra por SQL una reserva reprogramada (RESCHEDULED) que ya ocupa
        // la franja. Ningun endpoint del alcance actual produce este estado, asi que la
        // siembra directa es la unica via para reproducir el defecto.
        LocalDateTime inicio = manana.atTime(9, 0);
        LocalDateTime fin = manana.atTime(10, 0);
        insertBooking(customerId, professionalId, serviceId, inicio, fin, "RESCHEDULED");

        // Act: se intenta insertar una segunda reserva CONFIRMADA en la MISMA franja.
        // El estado RESCHEDULED queda fuera del predicado de la exclusion de V46,
        // de modo que la base deberia aceptarlo y es justamente lo que hay que detectar.
        Object resultado = intentoDeInsertarReserva(inicio, fin, "CONFIRMADA");

        // Assert: la base debe rechazar el solapamiento con una violacion de integridad.
        assertThat(resultado)
                .as("Una reserva CONFIRMADA no puede solaparse con una RESCHEDULED que ya ocupa la "
                        + "franja (decision de negocio: RESCHEDULED ocupa agenda). "
                        + "Resultado del insert: %s", resultado)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ----------------------------------------------------------------------------------
    // S3-03: RESCHEDULED debe excluirse de la disponibilidad igual que CONFIRMADA.
    // ----------------------------------------------------------------------------------

    @Test
    void unaReservaReprogramadaNoSeOfreceComoFranjaLibre() {
        // Arrange: una franja 08:00-12:00 con una reserva CONFIRMADA en 09:00-10:00.
        LocalDateTime inicio = manana.atTime(9, 0);
        LocalDateTime fin = manana.atTime(10, 0);
        UUID reservaId = insertBooking(customerId, professionalId, serviceId, inicio, fin, "CONFIRMADA");

        // Act: primero se mide la disponibilidad con la reserva confirmada.
        List<LocalDateTime> slotsConConfirmada = iniciosDeLaDisponibilidad();

        // Act: la misma reserva pasa a RESCHEDULED y se vuelve a medir.
        jdbc.update("UPDATE bookings SET status = 'RESCHEDULED' WHERE id = ?", reservaId);
        List<LocalDateTime> slotsConReprogramada = iniciosDeLaDisponibilidad();

        // Assert: la disponibilidad es identica; RESCHEDULED ocupa agenda igual que CONFIRMADA.
        assertThat(slotsConReprogramada)
                .as("Una reserva RESCHEDULED sigue ocupando la agenda: el conjunto de slots debe "
                        + "ser el mismo que con la reserva CONFIRMADA. Con CONFIRMADA: %s. "
                        + "Con RESCHEDULED: %s", slotsConConfirmada, slotsConReprogramada)
                .isEqualTo(slotsConConfirmada);

        // Assert: la franja reprogramada no se ofrece en ningun caso.
        assertThat(slotsConConfirmada)
                .as("La franja 09:00-10:00 esta ocupada por la reserva")
                .doesNotContain(inicio);
        assertThat(slotsConReprogramada)
                .as("La franja 09:00-10:00 no debe ofrecerse con la reserva reprogramada")
                .doesNotContain(inicio);
    }

    // ----------------------------------------------------------------------------------
    // Control: una reserva CANCELADA si libera la franja (linea base del modulo).
    // ----------------------------------------------------------------------------------

    @Test
    void unaReservaCanceladaSiLiberaLaFranja() {
        // Arrange
        LocalDateTime inicio = manana.atTime(9, 0);
        LocalDateTime fin = manana.atTime(10, 0);
        insertBooking(customerId, professionalId, serviceId, inicio, fin, "CANCELADA");

        // Act
        List<LocalDateTime> slots = iniciosDeLaDisponibilidad();

        // Assert: la franja cancelada vuelve a ofrecerse.
        assertThat(slots)
                .as("Una reserva cancelada libera la franja y debe volver a ofrecerse. "
                        + "Slots: %s", slots)
                .contains(inicio);
    }

    // ----------------------------------------------------------------------------------
    // Ayudantes
    // ----------------------------------------------------------------------------------

    /**
     * Intenta insertar una reserva y devuelve la excepcion <em>como valor</em>.
     *
     * <p>Devolverla en lugar de propagarla permite distinguir "la base rechazo el solapamiento"
     * (que es lo correcto) de "la base lo acepto" (que es el defecto S3-10).
     */
    private Object intentoDeInsertarReserva(LocalDateTime inicio, LocalDateTime fin, String status) {
        try {
            jdbc.update("INSERT INTO bookings (id, customer_id, professional_id, service_id, "
                            + "start_time, end_time, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), customerId, professionalId, serviceId, inicio, fin, status,
                    LocalDateTime.now(), LocalDateTime.now());
            return null;
        } catch (Exception exception) {
            return exception;
        }
    }

    /** Horas de inicio ofrecidas para el profesional en el dia de la prueba. */
    private List<LocalDateTime> iniciosDeLaDisponibilidad() {
        AvailabilityResponse response = availabilityService.find(professionalId, serviceId, manana, manana);
        assertThat(response.days())
                .as("El profesional debe tener disponibilidad en el dia de la prueba")
                .hasSize(1);
        return response.days().get(0).slots().stream()
                .map(AvailabilityResponse.Block::startTime)
                .toList();
    }
}
