package com.bookly.backendcf.report.application;

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

    @Transactional(readOnly = true)
    public OccupancyReportResponse generate(ReportPeriod requestedPeriod) {
        ReportPeriod period = requestedPeriod == null ? ReportPeriod.MES : requestedPeriod;
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(period.getDays() - 1L);

        List<BookedPeriod> bookings = occupancyQuery.findOccupyingBookings(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        List<WeeklyWindow> windows = occupancyQuery.findWeeklyWindows();

        List<DayOccupancy> byDay = buildByDay(bookings, windows, from, to);
        List<ProfessionalOccupancy> byProfessional = buildByProfessional(bookings, windows, from, to);

        long totalBooked = byDay.stream().mapToLong(DayOccupancy::bookedMinutes).sum();
        long totalCapacity = byDay.stream().mapToLong(DayOccupancy::capacityMinutes).sum();

        return new OccupancyReportResponse(
                period, from, to,
                OccupancyCalculator.percent(totalBooked, totalCapacity),
                byDay, byProfessional);
    }

    private List<DayOccupancy> buildByDay(
            List<BookedPeriod> bookings, List<WeeklyWindow> windows, LocalDate from, LocalDate to) {
        Map<DayOfWeek, Long> booked = OccupancyCalculator.bookedByDay(bookings);
        Map<DayOfWeek, Long> capacity = OccupancyCalculator.capacityByDay(windows, from, to);

        return java.util.Arrays.stream(DayOfWeek.values())
                .map(day -> new DayOccupancy(
                        day,
                        booked.get(day),
                        capacity.get(day),
                        OccupancyCalculator.percent(booked.get(day), capacity.get(day))))
                .toList();
    }

    private List<ProfessionalOccupancy> buildByProfessional(
            List<BookedPeriod> bookings, List<WeeklyWindow> windows, LocalDate from, LocalDate to) {
        Map<UUID, Long> booked = OccupancyCalculator.bookedByProfessional(bookings);
        Map<UUID, Long> capacity = OccupancyCalculator.capacityByProfessional(windows, from, to);
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
                            OccupancyCalculator.percent(bookedMinutes, capacityMinutes));
                })
                .sorted(Comparator.comparing(ProfessionalOccupancy::professionalName))
                .toList();
    }
}