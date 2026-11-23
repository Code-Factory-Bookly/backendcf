package com.bookly.backendcf.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** #213, #215 y #217: capacidad con unión de franjas y bloqueos, y porcentaje exacto. */
class OccupancyCalculatorCapacityTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 16);
    private static final LocalDate DOMINGO = LUNES.plusDays(6);

    private final UUID ana = UUID.randomUUID();
    private final UUID luis = UUID.randomUUID();

    // Lunes 08:00-12:00 y 10:00-14:00 se solapan: la unión es 08:00-14:00 (360 minutos), no 480.
    private final List<WeeklyWindow> franjas = List.of(
            new WeeklyWindow(ana, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)),
            new WeeklyWindow(ana, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(14, 0)));

    @Test
    void lasFranjasSolapadasNoSeSumanDosVeces() {
        Map<UUID, Long> capacidad = OccupancyCalculator.availableByProfessional(franjas, List.of(), LUNES, DOMINGO);

        assertThat(capacidad).containsEntry(ana, 360L);
    }

    @Test
    void losBloqueosDescuentanSoloLoQueCaeDentroDeLasFranjas() {
        List<BlockedPeriod> bloqueos = List.of(
                // 1 hora dentro de la franja
                new BlockedPeriod(ana, LUNES.atTime(9, 0), LUNES.atTime(10, 0)),
                // viene del domingo anterior y cubre 08:00-08:30
                new BlockedPeriod(ana, LUNES.minusDays(1).atTime(23, 0), LUNES.atTime(8, 30)),
                // fuera de la franja: no descuenta
                new BlockedPeriod(ana, LUNES.atTime(18, 0), LUNES.atTime(19, 0)),
                // de otro profesional: no descuenta
                new BlockedPeriod(luis, LUNES.atTime(8, 0), LUNES.atTime(14, 0)),
                // otro día: no descuenta
                new BlockedPeriod(ana, LUNES.plusDays(1).atTime(8, 0), LUNES.plusDays(1).atTime(9, 0)));

        Map<UUID, Long> porProfesional = OccupancyCalculator.availableByProfessional(franjas, bloqueos, LUNES, DOMINGO);
        Map<DayOfWeek, Long> porDia = OccupancyCalculator.availableByDay(franjas, bloqueos, LUNES, DOMINGO);

        assertThat(porProfesional).containsEntry(ana, 270L);
        assertThat(porDia).hasSize(7).containsEntry(DayOfWeek.MONDAY, 270L).containsEntry(DayOfWeek.TUESDAY, 0L);
    }

    @Test
    void unBloqueoDeTodoElDiaDejaLaCapacidadEnCero() {
        List<BlockedPeriod> bloqueos = List.of(
                new BlockedPeriod(ana, LUNES.atStartOfDay(), LUNES.plusDays(1).atStartOfDay()));

        assertThat(OccupancyCalculator.availableByProfessional(franjas, bloqueos, LUNES, LUNES))
                .containsEntry(ana, 0L);
    }

    @Test
    void elPorcentajeNoSeRecortaA100() {
        assertThat(OccupancyCalculator.exactPercent(300, 240)).isEqualTo(125.0);
        assertThat(OccupancyCalculator.exactPercent(1, 3)).isEqualTo(33.33);
    }

    @Test
    void sinCapacidadElPorcentajeEsCeroSinReservasYNuloConReservas() {
        assertThat(OccupancyCalculator.exactPercent(0, 0)).isEqualTo(0.0);
        assertThat(OccupancyCalculator.exactPercent(60, 0)).isNull();
    }
}
