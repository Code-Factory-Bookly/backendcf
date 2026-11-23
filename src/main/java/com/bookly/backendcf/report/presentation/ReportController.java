package com.bookly.backendcf.report.presentation;

import com.bookly.backendcf.report.application.OccupancyReportService;
import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/reportes")
public class ReportController {

    static final int MAX_RANGE_DAYS = 366;

    private final OccupancyReportService occupancyReportService;

    public ReportController(OccupancyReportService occupancyReportService) {
        this.occupancyReportService = occupancyReportService;
    }

    // required = false a propósito, igual que en disponibilidad: si faltara el parámetro, Spring
    // reenviaría a /error y la seguridad lo convertiría en 401 (BUG-002). El servicio usa MES por defecto.
    // #221: además de la ventana fija (periodo), acepta un rango propio con desde y hasta (ambos incluidos,
    // máximo MAX_RANGE_DAYS días). Si se envía el rango, el periodo se ignora y la respuesta lo trae nulo.
    @GetMapping("/ocupacion")
    public OccupancyReportResponse occupancy(
            @RequestParam(name = "periodo", required = false) ReportPeriod period,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null && to == null) {
            return occupancyReportService.generate(period);
        }
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envía desde y hasta juntos");
        }
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha hasta no puede ser anterior a desde");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El rango máximo del reporte es de " + MAX_RANGE_DAYS + " días");
        }
        return occupancyReportService.generate(from, to);
    }
}