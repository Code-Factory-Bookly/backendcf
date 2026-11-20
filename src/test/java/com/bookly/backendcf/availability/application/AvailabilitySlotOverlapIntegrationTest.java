package com.bookly.backendcf.availability.application;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * S3-07 (BAJA) · HU-07: la disponibilidad no debe ofrecer dos veces la misma hora de inicio.
 *
 * <p><b>Defecto que se reproduce.</b> {@code AvailabilityCalculator.freeSlots} recorre cada
 * {@code WorkingWindow} del dia de forma independiente y, dentro de cada franja, genera los slots
 * con un cursor propio. No fusiona las franjas que se solapan ni deduplica el {@code startTime} al
 * agregar. Con dos franjas 09:00-12:00 y 11:00-14:00, y un servicio de 60 minutos, la respuesta
 * trae seis slots: 09:00, 10:00, 11:00, <b>11:00</b>, 12:00, 13:00. El cliente ve la misma hora
 * ofrecida dos veces.
 *
 * <p><b>Alcance real (veredicto del debugging sistematico).</b> El defecto es real en el codigo,
 * pero <b>no hay ruta de usuario</b>: la API rechaza el solape con 409 {@code SCHEDULE_CONFLICT}
 * antes de llegar a la base de datos, y una busqueda exhaustiva de todas las combinaciones de dos
 * y tres franjas disjuntas no encontro ningun contraejemplo. Por eso la severidad bajo de MEDIA a
 * <b>BAJA</b> y el bug se reporto como deuda de robustez latente, no como bug de Sprint 3. La
 * solucion de raiz (fusionar las ventanas del dia) es la misma que resuelve S3-05.
 *
 * <p>La causa raiz compartida es que la base de datos solo impide dos franjas que empiecen a la
 * misma hora ({@code uk_horario_semanal_inicio}); el solapamiento parcial lo permite. Es la
 * contraparte exacta de S3-05 en el modulo de disponibilidad: alli la no fusion infla la
 * capacidad, aqui la no fusion duplica la oferta.
 *
 * <p><b>S3-03 (deuda de estado) tambien se cubre aqui.</b> La disponibilidad consulta
 * {@code findOverlappingBookings}, que filtra por {@code status = 'CONFIRMADA'}, mientras que el
 * reporte de ocupacion cuenta {@code CONFIRMADA} y {@code COMPLETED}. Una reserva ya atendida
 * vuelve a aparecer como reservable: el cliente puede reservar una franja que el reporte ya
 * considera ocupada. El debugging sistematico refuto S3-03 como bug de usuario (la API solo
 * produce CONFIRMADA y CANCELADA, y en ambos estados los dos modulos coinciden); lo que queda es
 * la duplicacion de las listas de estados, que este caso documenta. Este caso compara el conjunto
 * de slots con la reserva en CONFIRMADA y con la misma reserva en COMPLETED, y exige que sean
 * identicos.
 */
@Tag("integration")
@SpringBootTest
class AvailabilitySlotOverlapIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AvailabilityService availabilityService;

    private final UUID professionalId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    /** Mañana siguiente: siempre futuro, porque el servicio rechaza rangos en el pasado. */
    private final LocalDate manana = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() {
        insertProfessional(professionalId, uniqueEmail("profesional"), "Profesional Disponibilidad");
        insertUser(customerId, uniqueEmail("cliente"), "CUSTOMER", "Cliente Disponibilidad");
        insertService(serviceId, "Servicio Disponibilidad " + suffix, 60);
    }

    @AfterEach
    void cleanUp() {
        limpiarBaseDeDatos();
    }

    // ----------------------------------------------------------------------------------
    // S3-07 (BAJA): las horas de inicio devueltas deben ser unicas.
    // ----------------------------------------------------------------------------------

    @Test
    void lasHorasDeInicioDevueltasNoSeRepitenConFranjasSolapadas() {
        // Arrange: dos franjas del mismo dia que se solapan parcialmente
        // (09:00-12:00 y 11:00-14:00). Con un servicio de 60 minutos, la union real
        // 09:00-14:00 ofrece slots unicos 09:00, 10:00, 11:00, 12:00, 13:00.
        insertWeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        insertWeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(14, 0));

        // Act
        AvailabilityResponse response = availabilityService.find(professionalId, serviceId, manana, manana);

        // Assert: ninguna hora de inicio aparece dos veces.
        List<LocalDateTime> inicios = response.days().get(0).slots()
                .stream()
                .map(AvailabilityResponse.Block::startTime)
                .toList();

        assertThat(inicios)
                .as("La disponibilidad no debe ofrecer la misma hora de inicio mas de una vez. "
                        + "Horas devueltas: %s", inicios)
                .doesNotHaveDuplicates();

        // Assert: ademas, el numero de slots corresponde a la union real (5 horas de 60 minutos).
        assertThat(inicios)
                .as("Con franjas solapadas que cubren 09:00-14:00 y un servicio de 60 minutos, "
                        + "la union real ofrece 5 slots. Slots devueltos: %s", inicios)
                .hasSize(5);
    }

    // ----------------------------------------------------------------------------------
    // S3-03 (deuda de estado): una reserva COMPLETED no debe reaparecer en la disponibilidad.
    // ----------------------------------------------------------------------------------

    @Test
    void unaReservaCompletadaNoVuelveAOfrecerseComoReservable() {
        // Arrange: una franja 08:00-12:00 y una reserva de 60 minutos en 09:00-10:00.
        insertWeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
        UUID reservaId = insertBooking(customerId, professionalId, serviceId,
                manana.atTime(9, 0), manana.atTime(10, 0), "CONFIRMADA");

        // Act: primero se mide el conjunto de slots con la reserva confirmada.
        List<LocalDateTime> slotsConConfirmada = iniciosDeLaDisponibilidad();

        // Act: la misma reserva pasa a COMPLETED (una atencion ya ocurrida) y se vuelve a medir.
        jdbc.update("UPDATE bookings SET status = 'COMPLETED' WHERE id = ?", reservaId);
        List<LocalDateTime> slotsConCompletada = iniciosDeLaDisponibilidad();

        // Assert: el conjunto de slots debe ser identico: una atencion ya ocurrida sigue
        // ocupando la agenda, igual que en el reporte de ocupacion (HU-15).
        assertThat(slotsConCompletada)
                .as("Una reserva COMPLETED sigue ocupando la agenda: el conjunto de slots debe "
                        + "ser el mismo que con la reserva CONFIRMADA. Con CONFIRMADA: %s. "
                        + "Con COMPLETED: %s", slotsConConfirmada, slotsConCompletada)
                .isEqualTo(slotsConConfirmada);

        // Assert: la franja de la reserva atendida no se ofrece en ningun caso.
        assertThat(slotsConConfirmada)
                .as("La franja 09:00-10:00 esta ocupada por la reserva")
                .doesNotContain(manana.atTime(9, 0));
        assertThat(slotsConCompletada)
                .as("La franja 09:00-10:00 sigue ocupada aunque la reserva este COMPLETED")
                .doesNotContain(manana.atTime(9, 0));
    }

    // ----------------------------------------------------------------------------------
    // Control: sin franjas solapadas los horarios no se repiten (linea base del modulo).
    // ----------------------------------------------------------------------------------

    @Test
    void conFranjasContiguasLasHorasDeInicioTambienSonUnicas() {
        // Arrange: dos franjas contiguas, 08:00-10:00 y 10:00-12:00 (la union es 08:00-12:00).
        insertWeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0));
        insertWeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0));

        // Act
        AvailabilityResponse response = availabilityService.find(professionalId, serviceId, manana, manana);

        // Assert: el comportamiento correcto ya se cumple con franjas contiguas.
        List<LocalDateTime> inicios = response.days().get(0).slots()
                .stream()
                .map(AvailabilityResponse.Block::startTime)
                .toList();
        assertThat(inicios).doesNotHaveDuplicates();
    }

    // ----------------------------------------------------------------------------------
    // Ayudantes
    // ----------------------------------------------------------------------------------

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
