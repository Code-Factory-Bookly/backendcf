package com.bookly.backendcf.report.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Puerto de consultas agregadas del reporte general de HU-16. */
public interface GeneralReportQuery {

    long countReservations(LocalDateTime from, LocalDateTime to);

    long countCancelledReservations(LocalDateTime from, LocalDateTime to);

    BigDecimal sumRecognizedRevenue(LocalDateTime from, LocalDateTime to);

    List<ResourceUsage> findResourceUsage(LocalDateTime from, LocalDateTime to);

    record ResourceUsage(UUID resourceId, String resourceName, int totalQuantity, long bookingsUsingResource) {
    }
}
