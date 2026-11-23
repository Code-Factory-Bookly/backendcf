package com.bookly.backendcf.availability.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** #217: dos franjas solapadas del mismo día se ofrecen como una sola, sin huecos repetidos. */
class AvailabilityCalculatorMergeTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 16);
    private static final LocalDateTime ANTES = LUNES.minusDays(1).atStartOfDay();
    private static final Duration UNA_HORA = Duration.ofMinutes(60);

    @Test
    void lasFranjasSolapadasNoRepitenHuecos() {
        List<WorkingWindow> franjas = List.of(
                new WorkingWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0)),
                new WorkingWindow(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)));

        List<AvailableSlot> huecos = AvailabilityCalculator.freeSlots(LUNES, franjas, UNA_HORA, List.of(), ANTES);

        assertThat(huecos).doesNotHaveDuplicates();
        assertThat(huecos).extracting(AvailableSlot::startTime).contains(LUNES.atTime(8, 0), LUNES.atTime(10, 0));
        assertThat(huecos).allSatisfy(h -> assertThat(h.endTime()).isBeforeOrEqualTo(LUNES.atTime(11, 0)));
    }

    @Test
    void unaFranjaContenidaEnOtraNoAgregaHuecos() {
        WorkingWindow grande = new WorkingWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
        WorkingWindow contenida = new WorkingWindow(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertThat(AvailabilityCalculator.freeSlots(LUNES, List.of(grande, contenida), UNA_HORA, List.of(), ANTES))
                .isEqualTo(AvailabilityCalculator.freeSlots(LUNES, List.of(grande), UNA_HORA, List.of(), ANTES));
    }
}
