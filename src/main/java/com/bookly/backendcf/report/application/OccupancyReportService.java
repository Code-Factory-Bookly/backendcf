package com.bookly.backendcf.report.application;

import com.bookly.backendcf.report.domain.model.BlockedPeriod;
import com.bookly.backendcf.report.domain.model.BookedPeriod;
import com.bookly.backendcf.report.domain.model.OccupancyCalculator;
import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.domain.model.WeeklyWindow;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse.DayOccupancy;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse.ProfessionalOccupancy;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Caso de uso de HU-15: reporte de ocupación de las agendas por día de la semana y por profesional. */
@Service
public class OccupancyReportService {

    private final OccupancyQuery occupancyQuery;

    public OccupancyReportService(OccupancyQuery occupancyQuery) {
        this.occupancyQuery = occupancyQuery;
    }

    /** Ventana fija que termina hoy: los últimos 7 (SEMANA) o 30 días (MES, por defecto). */
    @Transactional(readOnly = true)
    public OccupancyReportResponse generate(ReportPeriod requestedPeriod) {
        ReportPeriod period = requestedPeriod == null ? ReportPeriod.MES : requestedPeriod;
        LocalDate to = LocalDate.now();
        return build(period, to.minusDays(period.getDays() - 1L), to);
    }

    /** #221: rango de fechas elegido por el administrador (ambos días incluidos); el periodo va nulo. */
    @Transactional(readOnly = true)
    public OccupancyReportResponse generate(LocalDate from, LocalDate to) {
        return build(null, from, to);
    }

    private OccupancyReportResponse build(ReportPeriod period, LocalDate from, LocalDate to) {
        List<BookedPeriod> bookings = occupancyQuery.findOccupyingBookings(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        List<WeeklyWindow> windows = occupancyQuery.findWeeklyWindows();
        List<BlockedPeriod> blocks = occupancyQuery.findAgendaBlocks(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        List<DayOccupancy> byDay = buildByDay(bookings, windows, blocks, from, to);
        List<ProfessionalOccupancy> byProfessional = buildByProfessional(bookings, windows, blocks, from, to);

        long totalBooked = byDay.stream().mapToLong(DayOccupancy::bookedMinutes).sum();
        long totalCapacity = byDay.stream().mapToLong(DayOccupancy::capacityMinutes).sum();

        return new OccupancyReportResponse(
                period, from, to,
                OccupancyCalculator.exactPercent(totalBooked, totalCapacity),
                byDay, byProfessional);
    }

    private List<DayOccupancy> buildByDay(List<BookedPeriod> bookings, List<WeeklyWindow> windows,
                                          List<BlockedPeriod> blocks, LocalDate from, LocalDate to) {
        Map<DayOfWeek, Long> booked = OccupancyCalculator.bookedByDay(bookings);
        Map<DayOfWeek, Long> capacity = OccupancyCalculator.availableByDay(windows, blocks, from, to);

        return java.util.Arrays.stream(DayOfWeek.values())
                .map(day -> new DayOccupancy(
                        day,
                        booked.get(day),
                        capacity.get(day),
                        OccupancyCalculator.exactPercent(booked.get(day), capacity.get(day))))
                .toList();
    }

    private List<ProfessionalOccupancy> buildByProfessional(List<BookedPeriod> bookings, List<WeeklyWindow> windows,
                                                           List<BlockedPeriod> blocks, LocalDate from, LocalDate to) {
        Map<UUID, Long> booked = OccupancyCalculator.bookedByProfessional(bookings);
        Map<UUID, Long> capacity = OccupancyCalculator.availableByProfessional(windows, blocks, from, to);
        List<UUID> professionalIds = occupancyQuery.findProfessionalIds();
        Map<UUID, String> names = occupancyQuery.professionalNames(professionalIds);

        return professionalIds.stream()
                .map(id -> {
                    long bookedMinutes = booked.getOrDefault(id, 0L);
                    long capacityMinutes = capacity.getOrDefault(id, 0L);
                    return new ProfessionalOccupancy(
                            id,
                            names.getOrDefault(id, ""),
                            bookedMinutes,
                            capacityMinutes,
                            OccupancyCalculator.exactPercent(bookedMinutes, capacityMinutes));
                })
                .sorted(Comparator.comparing(ProfessionalOccupancy::professionalName))
                .toList();
    }
}