package com.bookly.backendcf.report.presentation.dto;

import com.bookly.backendcf.report.domain.model.ReportPeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GeneralReportResponse(
        ReportPeriod period,
        LocalDate from,
        LocalDate to,
        long totalReservations,
        long cancelledReservations,
        double averageCancellationsPerDay,
        BigDecimal recognizedRevenue,
        List<ResourceUsage> resourceUsage,
        String message) {

    public record ResourceUsage(
            UUID resourceId,
            String resourceName,
            int totalQuantity,
            long bookingsUsingResource) {
    }
}
