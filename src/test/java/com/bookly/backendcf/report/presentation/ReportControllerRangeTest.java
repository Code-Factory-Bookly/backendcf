package com.bookly.backendcf.report.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.report.application.OccupancyReportService;
import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** #221: el reporte de ocupación acepta un rango propio con desde y hasta. */
class ReportControllerRangeTest {

    private final OccupancyReportService service = mock(OccupancyReportService.class);
    private final ReportController controller = new ReportController(service);
    private final LocalDate desde = LocalDate.of(2026, 11, 1);
    private final LocalDate hasta = LocalDate.of(2026, 11, 30);

    @Test
    void sinRangoUsaElPeriodo() {
        OccupancyReportResponse expected = response(ReportPeriod.SEMANA);
        when(service.generate(ReportPeriod.SEMANA)).thenReturn(expected);

        assertThat(controller.occupancy(ReportPeriod.SEMANA, null, null)).isSameAs(expected);
    }

    @Test
    void conRangoUsaLasFechasEnviadas() {
        OccupancyReportResponse expected = response(null);
        when(service.generate(desde, hasta)).thenReturn(expected);

        assertThat(controller.occupancy(ReportPeriod.MES, desde, hasta)).isSameAs(expected);
        verify(service, never()).generate(any(ReportPeriod.class));
    }

    @Test
    void desdeSinHastaEsUn400() {
        assertBadRequest(desde, null);
        assertBadRequest(null, hasta);
    }

    @Test
    void hastaAnteriorADesdeEsUn400() {
        assertBadRequest(hasta, desde);
    }

    @Test
    void unRangoDeMasDe366DiasEsUn400() {
        assertBadRequest(desde, desde.plusDays(ReportController.MAX_RANGE_DAYS));
    }

    @Test
    void unRangoDeExactamente366DiasEsValido() {
        LocalDate fin = desde.plusDays(ReportController.MAX_RANGE_DAYS - 1L);
        when(service.generate(desde, fin)).thenReturn(response(null));

        assertThat(controller.occupancy(null, desde, fin)).isNotNull();
    }

    private void assertBadRequest(LocalDate from, LocalDate to) {
        assertThatThrownBy(() -> controller.occupancy(null, from, to))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private OccupancyReportResponse response(ReportPeriod period) {
        return new OccupancyReportResponse(period, desde, hasta, 0.0, List.of(), List.of());
    }
}
