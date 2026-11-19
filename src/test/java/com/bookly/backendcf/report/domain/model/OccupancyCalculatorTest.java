package com.bookly.backendcf.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de la lógica pura del reporte de ocupación (HU-15).
 *
 * <p>OccupancyCalculator es una clase final con métodos estáticos: no se puede mockear, así que se
 * prueban directamente sus métodos. Todas las fechas son relativas a hoy para que los casos nunca
 * expiren.
 */
@Tag("unit")
class OccupancyCalculatorTest {

    private final UUID professionalId = UUID.randomUUID();
    private final UUID otroProfessionalId = UUID.randomUUID();

    /** Rango fijo de una semana completa (de lunes a domingo) que contiene todos los días de la semana. */
    private final LocalDate monday = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    // ------------------------------------------------------------------------------------------
    // percent(): porcentajes
    // ------------------------------------------------------------------------------------------

    @Test
    void calculaElPorcentajeDeOcupacionSobreLaCapacidad() {
        // Arrange
        long bookedMinutes = 300L;
        long capacityMinutes = 1200L;

        // Act
        double percent = OccupancyCalculator.percent(bookedMinutes, capacityMinutes);

        // Assert
        assertThat(percent).isEqualTo(25.0);
    }

    @Test
    void devuelveCeroCuandoLaCapacidadEsCeroYNoDivideEntreCero() {
        // Arrange
        long bookedMinutes = 120L;
        long capacityMinutes = 0L;

        // Act
        double percent = OccupancyCalculator.percent(bookedMinutes, capacityMinutes);

        // Assert: si percent() dividiera entre cero, la llamada de arriba ya habria fallado.
        assertThat(percent).isZero();
    }

    @Test
    void devuelveCeroCuandoLaCapacidadEsNegativa() {
        // Arrange
        long bookedMinutes = 120L;
        long capacityMinutes = -60L;

        // Act
        double percent = OccupancyCalculator.percent(bookedMinutes, capacityMinutes);

        // Assert
        assertThat(percent).isZero();
    }

    @Test
    void limitaElPorcentajeAlTopeDeCienCuandoLoReservadoExcedeLaCapacidad() {
        // Arrange
        long bookedMinutes = 1500L;
        long capacityMinutes = 600L;

        // Act
        double percent = OccupancyCalculator.percent(bookedMinutes, capacityMinutes);

        // Assert
        assertThat(percent).isEqualTo(100.0);
    }

    @Test
    void redondeaElPorcentajeADosDecimales() {
        // Arrange
        long bookedMinutes = 100L;
        long capacityMinutes = 300L; // 33.333...%

        // Act
        double percent = OccupancyCalculator.percent(bookedMinutes, capacityMinutes);

        // Assert
        assertThat(percent).isEqualTo(33.33);
    }

    @Test
    void redondeaElPorcentajeADosDecimalesEnElLimiteInferior() {
        // Arrange
        long bookedMinutes = 2L;
        long capacityMinutes = 3L; // 66.666...%

        // Act
        double percent = OccupancyCalculator.percent(bookedMinutes, capacityMinutes);

        // Assert
        assertThat(percent).isEqualTo(66.67);
    }

    @Test
    void devuelveCienCuandoLaCapacidadEstaCompletamenteReservada() {
        // Arrange
        long bookedMinutes = 480L;
        long capacityMinutes = 480L;

        // Act
        double percent = OccupancyCalculator.percent(bookedMinutes, capacityMinutes);

        // Assert
        assertThat(percent).isEqualTo(100.0);
    }

    @Test
    void devuelveCeroCuandoNoHayMinutosReservados() {
        // Arrange
        long bookedMinutes = 0L;
        long capacityMinutes = 480L;

        // Act
        double percent = OccupancyCalculator.percent(bookedMinutes, capacityMinutes);

        // Assert
        assertThat(percent).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // capacityByDay(): los 7 días siempre presentes
    // ------------------------------------------------------------------------------------------

    @Test
    void laCapacidadPorDiaTraeSiempreLosSieteDiasEnCero() {
        // Arrange: no hay ninguna franja configurada
        List<WeeklyWindow> windows = List.of();
        LocalDate from = monday;
        LocalDate to = monday.plusDays(6);

        // Act
        Map<DayOfWeek, Long> capacity = OccupancyCalculator.capacityByDay(windows, from, to);

        // Assert
        assertThat(capacity).hasSize(7);
        assertThat(capacity).containsOnlyKeys(DayOfWeek.values());
        assertThat(capacity.values()).containsOnly(0L);
    }

    @Test
    void laCapacidadPorDiaTraeSiempreLosSieteDiasAunConFranjasDeUnSoloDia() {
        // Arrange
        List<WeeklyWindow> windows = List.of(
                window(professionalId, DayOfWeek.MONDAY, "08:00", "12:00"));
        LocalDate from = monday;
        LocalDate to = monday.plusDays(6);

        // Act
        Map<DayOfWeek, Long> capacity = OccupancyCalculator.capacityByDay(windows, from, to);

        // Assert
        assertThat(capacity).hasSize(7);
        assertThat(capacity.get(DayOfWeek.MONDAY)).isEqualTo(240L);
        assertThat(capacity.get(DayOfWeek.TUESDAY)).isZero();
        assertThat(capacity.get(DayOfWeek.SUNDAY)).isZero();
    }

    @Test
    void laCapacidadPorDiaSumaLosMinutosDeTodasLasFranjasDeEseDia() {
        // Arrange: dos franjas del mismo día y dos profesionales distintos
        List<WeeklyWindow> windows = List.of(
                window(professionalId, DayOfWeek.MONDAY, "08:00", "12:00"),
                window(professionalId, DayOfWeek.MONDAY, "14:00", "16:00"),
                window(otroProfessionalId, DayOfWeek.MONDAY, "08:00", "10:00"));
        LocalDate from = monday;
        LocalDate to = monday.plusDays(6);

        // Act
        Map<DayOfWeek, Long> capacity = OccupancyCalculator.capacityByDay(windows, from, to);

        // Assert: 240 + 120 + 120
        assertThat(capacity.get(DayOfWeek.MONDAY)).isEqualTo(480L);
    }

    @Test
    void laCapacidadPorDiaAcumulaCadaOcurrenciaDelDiaEnElRango() {
        // Arrange: cuatro lunes dentro de un rango de dos semanas
        List<WeeklyWindow> windows = List.of(
                window(professionalId, DayOfWeek.MONDAY, "08:00", "12:00"));
        LocalDate from = monday;
        LocalDate to = monday.plusDays(13);

        // Act
        Map<DayOfWeek, Long> capacity = OccupancyCalculator.capacityByDay(windows, from, to);

        // Assert: dos lunes x 240 minutos
        assertThat(capacity.get(DayOfWeek.MONDAY)).isEqualTo(480L);
    }

    @Test
    void laCapacidadPorDiaIgnoraLasFranjasDeDiasFueraDelRango() {
        // Arrange: el rango cubre solo lunes y martes, pero hay franja de miércoles
        List<WeeklyWindow> windows = List.of(
                window(professionalId, DayOfWeek.MONDAY, "08:00", "12:00"),
                window(professionalId, DayOfWeek.WEDNESDAY, "08:00", "12:00"));
        LocalDate from = monday;
        LocalDate to = monday.plusDays(1);

        // Act
        Map<DayOfWeek, Long> capacity = OccupancyCalculator.capacityByDay(windows, from, to);

        // Assert
        assertThat(capacity.get(DayOfWeek.MONDAY)).isEqualTo(240L);
        assertThat(capacity.get(DayOfWeek.WEDNESDAY)).isZero();
    }

    @Test
    void laCapacidadPorDiaEsCeroSiElRangoEstaInvertidoOConservaUnaSolaFecha() {
        // Arrange: rango invertido (from > to)
        List<WeeklyWindow> windows = List.of(
                window(professionalId, DayOfWeek.MONDAY, "08:00", "12:00"));

        // Act
        Map<DayOfWeek, Long> capacity = OccupancyCalculator.capacityByDay(windows, monday.plusDays(1), monday);

        // Assert
        assertThat(capacity).hasSize(7);
        assertThat(capacity.values()).containsOnly(0L);
    }

    // ------------------------------------------------------------------------------------------
    // bookedByDay(): los 7 días siempre presentes
    // ------------------------------------------------------------------------------------------

    @Test
    void losMinutosReservadosPorDiaTraenSiempreLosSieteDiasEnCero() {
        // Arrange
        List<BookedPeriod> bookings = List.of();

        // Act
        Map<DayOfWeek, Long> booked = OccupancyCalculator.bookedByDay(bookings);

        // Assert
        assertThat(booked).hasSize(7);
        assertThat(booked).containsOnlyKeys(DayOfWeek.values());
        assertThat(booked.values()).containsOnly(0L);
    }

    @Test
    void losMinutosReservadosPorDiaTraenSiempreLosSieteDiasAunConUnaSolaReserva() {
        // Arrange
        List<BookedPeriod> bookings = List.of(
                booking(professionalId, monday.atTime(9, 0), monday.atTime(10, 0)));

        // Act
        Map<DayOfWeek, Long> booked = OccupancyCalculator.bookedByDay(bookings);

        // Assert
        assertThat(booked).hasSize(7);
        assertThat(booked.get(DayOfWeek.MONDAY)).isEqualTo(60L);
        assertThat(booked.get(DayOfWeek.TUESDAY)).isZero();
    }

    @Test
    void losMinutosReservadosPorDiaSumanTodasLasReservasDeEseDia() {
        // Arrange
        List<BookedPeriod> bookings = List.of(
                booking(professionalId, monday.atTime(9, 0), monday.atTime(10, 0)),
                booking(professionalId, monday.atTime(11, 0), monday.atTime(12, 30)),
                booking(otroProfessionalId, monday.atTime(15, 0), monday.atTime(16, 0)));

        // Act
        Map<DayOfWeek, Long> booked = OccupancyCalculator.bookedByDay(bookings);

        // Assert: 60 + 90 + 60
        assertThat(booked.get(DayOfWeek.MONDAY)).isEqualTo(210L);
    }

    @Test
    void unaReservaQueCruzaLaMedianocheSeContaEnElDiaDeInicio() {
        // Arrange: la reserva empieza el lunes a las 23:00 y termina el martes a la 01:00
        List<BookedPeriod> bookings = List.of(
                booking(professionalId, monday.atTime(23, 0), monday.plusDays(1).atTime(1, 0)));

        // Act
        Map<DayOfWeek, Long> booked = OccupancyCalculator.bookedByDay(bookings);

        // Assert: los 120 minutos se imputan al lunes (día de inicio)
        assertThat(booked.get(DayOfWeek.MONDAY)).isEqualTo(120L);
        assertThat(booked.get(DayOfWeek.TUESDAY)).isZero();
    }

    @Test
    void unaReservaQueCruzaLaMedianocheImputaTodosLosMinutosAlDiaDeInicio() {
        // Arrange
        List<BookedPeriod> bookings = List.of(
                booking(professionalId, monday.atTime(22, 0), monday.plusDays(1).atTime(2, 0)));

        // Act
        long minutos = OccupancyCalculator.bookedByDay(bookings).get(DayOfWeek.MONDAY);

        // Assert: 4 horas completas, sin repartir
        assertThat(minutos).isEqualTo(240L);
    }

    // ------------------------------------------------------------------------------------------
    // capacityByProfessional() y bookedByProfessional()
    // ------------------------------------------------------------------------------------------

    @Test
    void laCapacidadPorProfesionalSumaLasFranjasDeCadaUno() {
        // Arrange
        List<WeeklyWindow> windows = List.of(
                window(professionalId, DayOfWeek.MONDAY, "08:00", "12:00"),
                window(professionalId, DayOfWeek.TUESDAY, "08:00", "10:00"),
                window(otroProfessionalId, DayOfWeek.MONDAY, "08:00", "10:00"));
        LocalDate from = monday;
        LocalDate to = monday.plusDays(6);

        // Act
        Map<UUID, Long> capacity = OccupancyCalculator.capacityByProfessional(windows, from, to);

        // Assert
        assertThat(capacity.get(professionalId)).isEqualTo(360L);
        assertThat(capacity.get(otroProfessionalId)).isEqualTo(120L);
    }

    @Test
    void laCapacidadPorProfesionalAcumulaLasRepeticionesDelDiaEnElRango() {
        // Arrange: el mismo profesional atiende todos los lunes de un mes
        List<WeeklyWindow> windows = List.of(
                window(professionalId, DayOfWeek.MONDAY, "08:00", "12:00"));
        LocalDate from = monday;
        LocalDate to = monday.plusDays(27);

        // Act
        Map<UUID, Long> capacity = OccupancyCalculator.capacityByProfessional(windows, from, to);

        // Assert: cuatro lunes x 240 minutos
        assertThat(capacity.get(professionalId)).isEqualTo(960L);
    }

    @Test
    void laCapacidadPorProfesionalNoIncluyeAQuienNoTieneFranjas() {
        // Arrange
        List<WeeklyWindow> windows = List.of(
                window(professionalId, DayOfWeek.MONDAY, "08:00", "12:00"));
        LocalDate from = monday;
        LocalDate to = monday.plusDays(6);

        // Act
        Map<UUID, Long> capacity = OccupancyCalculator.capacityByProfessional(windows, from, to);

        // Assert
        assertThat(capacity).doesNotContainKey(otroProfessionalId);
    }

    @Test
    void losMinutosReservadosPorProfesionalSumanTodasSusReservas() {
        // Arrange
        List<BookedPeriod> bookings = List.of(
                booking(professionalId, monday.atTime(9, 0), monday.atTime(10, 0)),
                booking(professionalId, monday.atTime(11, 0), monday.atTime(12, 30)),
                booking(otroProfessionalId, monday.atTime(15, 0), monday.atTime(16, 0)));

        // Act
        Map<UUID, Long> booked = OccupancyCalculator.bookedByProfessional(bookings);

        // Assert
        assertThat(booked.get(professionalId)).isEqualTo(150L);
        assertThat(booked.get(otroProfessionalId)).isEqualTo(60L);
    }

    @Test
    void losMinutosReservadosPorProfesionalQuedanVaciosSinReservas() {
        // Arrange
        List<BookedPeriod> bookings = List.of();

        // Act
        Map<UUID, Long> booked = OccupancyCalculator.bookedByProfessional(bookings);

        // Assert
        assertThat(booked).isEmpty();
    }

    @Test
    void unaReservaQueCruzaLaMedanocheSumaTodosLosMinutosAlProfesional() {
        // Arrange
        List<BookedPeriod> bookings = List.of(
                booking(professionalId, monday.atTime(23, 0), monday.plusDays(1).atTime(1, 0)));

        // Act
        Map<UUID, Long> booked = OccupancyCalculator.bookedByProfessional(bookings);

        // Assert
        assertThat(booked.get(professionalId)).isEqualTo(120L);
    }

    // ------------------------------------------------------------------------------------------
    // Ventanas que se solapan o son contiguas
    // ------------------------------------------------------------------------------------------

    @Test
    void dosFranjasContiguasSumanLaCapacidadCompletaSinHuecoNiSolape() {
        // Arrange: 08:00-12:00 y 12:00-14:00 del mismo día
        List<WeeklyWindow> windows = List.of(
                window(professionalId, DayOfWeek.MONDAY, "08:00", "12:00"),
                window(professionalId, DayOfWeek.MONDAY, "12:00", "14:00"));
        LocalDate from = monday;
        LocalDate to = monday.plusDays(6);

        // Act
        Map<DayOfWeek, Long> capacity = OccupancyCalculator.capacityByDay(windows, from, to);

        // Assert: 240 + 120 = 360 minutos, la unión de ambas franjas
        assertThat(capacity.get(DayOfWeek.MONDAY)).isEqualTo(360L);
    }

    @Test
    void dosFranjasQueSeSolapanSumanCadaUnaSusMinutos() {
        // Arrange: 08:00-12:00 y 10:00-14:00 se solapan en dos horas
        List<WeeklyWindow> windows = List.of(
                window(professionalId, DayOfWeek.MONDAY, "08:00", "12:00"),
                window(professionalId, DayOfWeek.MONDAY, "10:00", "14:00"));
        LocalDate from = monday;
        LocalDate to = monday.plusDays(6);

        // Act
        Map<DayOfWeek, Long> capacity = OccupancyCalculator.capacityByDay(windows, from, to);

        // Assert: el cálculo es la suma de las franjas (240 + 240), no la unión
        assertThat(capacity.get(DayOfWeek.MONDAY)).isEqualTo(480L);
    }

    @Test
    void unaFranjaDeDuracionCeroAportaCeroMinutosDeCapacidad() {
        // Arrange
        List<WeeklyWindow> windows = List.of(
                window(professionalId, DayOfWeek.MONDAY, "08:00", "08:00"));
        LocalDate from = monday;
        LocalDate to = monday.plusDays(6);

        // Act
        Map<DayOfWeek, Long> capacity = OccupancyCalculator.capacityByDay(windows, from, to);

        // Assert
        assertThat(capacity.get(DayOfWeek.MONDAY)).isZero();
    }

    @Test
    void unaReservaDeDuracionCeroNoAportaMinutosReservados() {
        // Arrange
        List<BookedPeriod> bookings = List.of(
                booking(professionalId, monday.atTime(9, 0), monday.atTime(9, 0)));

        // Act
        Map<DayOfWeek, Long> booked = OccupancyCalculator.bookedByDay(bookings);

        // Assert
        assertThat(booked.get(DayOfWeek.MONDAY)).isZero();
    }

    @Test
    void dosReservasContiguasDelMismoProfesionalSumanAmbasDuraciones() {
        // Arrange: 09:00-10:00 y 10:00-11:00, sin hueco entre ellas
        List<BookedPeriod> bookings = List.of(
                booking(professionalId, monday.atTime(9, 0), monday.atTime(10, 0)),
                booking(professionalId, monday.atTime(10, 0), monday.atTime(11, 0)));

        // Act
        Map<UUID, Long> booked = OccupancyCalculator.bookedByProfessional(bookings);

        // Assert
        assertThat(booked.get(professionalId)).isEqualTo(120L);
    }

    @Test
    void dosReservasQueSeSolapanSumanAmbasDuraciones() {
        // Arrange: 09:00-11:00 y 10:00-12:00
        List<BookedPeriod> bookings = List.of(
                booking(professionalId, monday.atTime(9, 0), monday.atTime(11, 0)),
                booking(professionalId, monday.atTime(10, 0), monday.atTime(12, 0)));

        // Act
        Map<UUID, Long> booked = OccupancyCalculator.bookedByProfessional(bookings);

        // Assert: 120 + 120 = 240
        assertThat(booked.get(professionalId)).isEqualTo(240L);
    }

    @Test
    void unaFranjaConHoraFinalAnteriorALaInicialNoGeneraUnPorcentajeNegativoNiNaN() {
        // Arrange: franja invertida. La base de datos la impide (ck_horario_semanal_franja exige
        // hora_fin > hora_inicio), pero el calculador no debe romperse si alguna vez llegara una.
        List<WeeklyWindow> windows = List.of(
                window(professionalId, DayOfWeek.MONDAY, "12:00", "08:00"));
        LocalDate from = monday;
        LocalDate to = monday.plusDays(6);

        // Act
        Map<DayOfWeek, Long> capacity = OccupancyCalculator.capacityByDay(windows, from, to);
        double percent = OccupancyCalculator.percent(
                capacity.get(DayOfWeek.MONDAY), capacity.get(DayOfWeek.MONDAY));

        // Assert: la capacidad sale negativa (-240) y percent() la trata como no positiva: 0 y no NaN
        assertThat(capacity.get(DayOfWeek.MONDAY)).isEqualTo(-240L);
        assertThat(percent).isZero();
        assertThat(percent).isNotNaN();
    }

    private WeeklyWindow window(UUID id, DayOfWeek day, String start, String end) {
        return new WeeklyWindow(id, day, LocalTime.parse(start), LocalTime.parse(end));
    }

    private BookedPeriod booking(UUID id, LocalDateTime start, LocalDateTime end) {
        return new BookedPeriod(id, start, end);
    }
}
