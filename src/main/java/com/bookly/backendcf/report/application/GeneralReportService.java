package com.bookly.backendcf.report.application;

import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.presentation.dto.GeneralReportResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Caso de uso de HU-16: métricas administrativas agregadas de la plataforma. */
@Service
public class GeneralReportService {

    private final GeneralReportQuery query;

    public GeneralReportService(GeneralReportQuery query) {
        this.query = query;
    }

    @Transactional(readOnly = true)
    public GeneralReportResponse generate(ReportPeriod requestedPeriod) {
        ReportPeriod period = requestedPeriod == null ? ReportPeriod.MES : requestedPeriod;
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(period.getDays() - 1L);

        long totalReservations = query.countReservations(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        long cancelledReservations = query.countCancelledReservations(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        BigDecimal revenue = query.sumRecognizedRevenue(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        return new GeneralReportResponse(
                period,
                from,
                to,
                totalReservations,
                cancelledReservations,
                (double) cancelledReservations / period.getDays(),
                revenue == null ? BigDecimal.ZERO : revenue,
                query.findResourceUsage(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
                        .map(resource -> new GeneralReportResponse.ResourceUsage(
                                resource.resourceId(),
                                resource.resourceName(),
                                resource.totalQuantity(),
                                resource.bookingsUsingResource()))
                        .toList(),
                totalReservations == 0
                        ? "No hay reservas registradas en el periodo consultado"
                        : null);
    }
}
