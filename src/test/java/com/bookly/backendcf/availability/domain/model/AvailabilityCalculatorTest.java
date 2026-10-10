package com.bookly.backendcf.availability.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailabilityCalculatorTest {

    private static final Duration ONE_HOUR = Duration.ofMinutes(60);

    private final LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    private final LocalDateTime now = LocalDateTime.now();

    @Test
    void generaBloquesConsecutivosDentroDeLaFranja() {
        var slots = AvailabilityCalculator.freeSlots(
                monday, List.of(window(DayOfWeek.MONDAY, "08:00", "12:00")), ONE_HOUR, List.of(), now);

        assertThat(slots).extracting(AvailableSlot::startTime)
                .containsExactly(at(8, 0), at(9, 0), at(10, 0), at(11, 0));
        assertThat(slots.get(3).endTime()).isEqualTo(at(12, 0));
    }

    @Test
    void descartaElBloqueQueNoCabeAlFinalDeLaFranja() {
        var slots = AvailabilityCalculator.freeSlots(
                monday, List.of(window(DayOfWeek.MONDAY, "08:00", "10:30")), ONE_HOUR, List.of(), now);

        assertThat(slots).hasSize(2);
    }

    @Test
    void excluyeLosBloquesQueSeSolapanConUnaReserva() {
        var busy = List.of(new BusyPeriod(at(9, 30), at(10, 30)));

        var slots = AvailabilityCalculator.freeSlots(
                monday, List.of(window(DayOfWeek.MONDAY, "08:00", "12:00")), ONE_HOUR, busy, now);

        assertThat(slots).extracting(AvailableSlot::startTime).containsExactly(at(8, 0), at(11, 0));
    }

    @Test
    void unaReservaContiguaNoBloqueaElBloqueSiguiente() {
        var busy = List.of(new BusyPeriod(at(8, 0), at(9, 0)));

        var slots = AvailabilityCalculator.freeSlots(
                monday, List.of(window(DayOfWeek.MONDAY, "08:00", "10:00")), ONE_HOUR, busy, now);

        assertThat(slots).extracting(AvailableSlot::startTime).containsExactly(at(9, 0));
    }

    @Test
    void ignoraLosBloquesQueYaPasaron() {
        LocalDateTime midMorning = at(9, 30);

        var slots = AvailabilityCalculator.freeSlots(
                monday, List.of(window(DayOfWeek.MONDAY, "08:00", "12:00")), ONE_HOUR, List.of(), midMorning);

        assertThat(slots).extracting(AvailableSlot::startTime).containsExactly(at(10, 0), at(11, 0));
    }

    @Test
    void ignoraLasFranjasDeOtrosDiasDeLaSemana() {
        var slots = AvailabilityCalculator.freeSlots(
                monday, List.of(window(DayOfWeek.TUESDAY, "08:00", "12:00")), ONE_HOUR, List.of(), now);

        assertThat(slots).isEmpty();
    }

    @Test
    void ordenaLasFranjasDelMismoDia() {
        var slots = AvailabilityCalculator.freeSlots(
                monday,
                List.of(window(DayOfWeek.MONDAY, "14:00", "16:00"), window(DayOfWeek.MONDAY, "08:00", "10:00")),
                ONE_HOUR, List.of(), now);

        assertThat(slots).extracting(AvailableSlot::startTime)
                .containsExactly(at(8, 0), at(9, 0), at(14, 0), at(15, 0));
    }

    @Test
    void rechazaUnaDuracionNoPositiva() {
        assertThatThrownBy(() -> AvailabilityCalculator.freeSlots(
                monday, List.of(), Duration.ZERO, List.of(), now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private WorkingWindow window(DayOfWeek day, String start, String end) {
        return new WorkingWindow(day, LocalTime.parse(start), LocalTime.parse(end));
    }

    private LocalDateTime at(int hour, int minute) {
        return monday.atTime(hour, minute);
    }
}