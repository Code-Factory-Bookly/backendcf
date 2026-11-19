package com.bookly.backendcf.report.presentation.dto;

import com.bookly.backendcf.report.domain.model.ReportPeriod;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OccupancyReportResponse(
        ReportPeriod period,
        LocalDate from,
        LocalDate to,
        double overallOccupancyPercent,
        List<DayOccupancy> byDayOfWeek,
        List<ProfessionalOccupancy> byProfessional) {

    public record DayOccupancy(
            DayOfWeek dayOfWeek,
            long bookedMinutes,
            long capacityMinutes,
            double occupancyPercent) {
    }

    public record ProfessionalOccupancy(
            UUID professionalId,
            String professionalName,
            long bookedMinutes,
            long capacityMinutes,
            double occupancyPercent) {
    }
}