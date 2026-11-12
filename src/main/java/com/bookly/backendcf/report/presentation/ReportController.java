package com.bookly.backendcf.report.presentation;

import com.bookly.backendcf.report.application.OccupancyReportService;
import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reportes")
public class ReportController {

    private final OccupancyReportService occupancyReportService;

    public ReportController(OccupancyReportService occupancyReportService) {
        this.occupancyReportService = occupancyReportService;
    }

    // required = false a propósito, igual que en disponibilidad: si faltara el parámetro, Spring
    // reenviaría a /error y la seguridad lo convertiría en 401 (BUG-002). El servicio usa MES por defecto.
    @GetMapping("/ocupacion")
    public OccupancyReportResponse occupancy(
            @RequestParam(name = "periodo", required = false) ReportPeriod period) {
        return occupancyReportService.generate(period);
    }
}