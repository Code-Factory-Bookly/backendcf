package com.bookly.backendcf.report.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.report.application.OccupancyReportService;
import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de seguridad y contrato HTTP del reporte de ocupación (HU-15).
 *
 * <p>Solo los administradores pueden consultar /api/v1/reportes/**; los demás roles reciben 403 y
 * las peticiones sin token reciben 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class ReportControllerSecurityTest {

    private static final String URL = "/api/v1/reportes/ocupacion";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private OccupancyReportService occupancyReportService;

    @BeforeEach
    void setUp() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(29);
        List<OccupancyReportResponse.DayOccupancy> days = List.of(
                new OccupancyReportResponse.DayOccupancy(DayOfWeek.MONDAY, 600L, 1200L, 50.0));
        when(occupancyReportService.generate(any()))
                .thenReturn(new OccupancyReportResponse(
                        ReportPeriod.MES, from, to, 50.0, days, List.of()));
    }

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(occupancyReportService, never()).generate(any());
    }

    @Test
    void elClienteRecibeForbiddenAlConsultarElReporte() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + tokenFor(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verify(occupancyReportService, never()).generate(any());
    }

    @Test
    void elProfesionalRecibeForbiddenAlConsultarElReporte() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + tokenFor(UserRole.PROFESSIONAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verify(occupancyReportService, never()).generate(any());
    }

    @Test
    void elAdministradorConsultaElReporteYRecibeLosPorcentajes() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("MES"))
                .andExpect(jsonPath("$.overallOccupancyPercent").value(50.0))
                .andExpect(jsonPath("$.byDayOfWeek").isArray())
                .andExpect(jsonPath("$.byDayOfWeek[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.byDayOfWeek[0].bookedMinutes").value(600))
                .andExpect(jsonPath("$.byDayOfWeek[0].capacityMinutes").value(1200))
                .andExpect(jsonPath("$.byDayOfWeek[0].occupancyPercent").value(50.0))
                .andExpect(jsonPath("$.byProfessional").isArray());
    }

    @Test
    void elAdministradorConsultaElReporteDelPeriodoSemana() throws Exception {
        mockMvc.perform(get(URL + "?periodo=SEMANA")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk());

        verify(occupancyReportService).generate(eq(ReportPeriod.SEMANA));
    }

    @Test
    void elAdministradorConsultaElReporteSinPeriodoYElServicioUsaElValorPorDefecto() throws Exception {
        // El parámetro es opcional a propósito: si faltara, Spring reenviaría a /error
        // y la seguridad lo convertiría en 401 (BUG-002).
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk());

        verify(occupancyReportService).generate(isNull());
    }

    @Test
    void elPeriodoEnMinusculasSeConvierteAlValorCorrespondiente() throws Exception {
        mockMvc.perform(get(URL + "?periodo=mes")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk());

        verify(occupancyReportService).generate(eq(ReportPeriod.MES));
    }

    @Test
    void unPeriodoDesconocidoDevuelveBadRequest() throws Exception {
        mockMvc.perform(get(URL + "?periodo=TRIMESTRE")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(occupancyReportService, never()).generate(any());
    }

    @Test
    void unPeriodoVacioSeTrataComoAusenteYNoComoUnError() throws Exception {
        mockMvc.perform(get(URL + "?periodo=")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk());

        verify(occupancyReportService).generate(isNull());
    }

    // El token solo lo acepta el filtro si la cuenta existe en la base (MantisBT BUG-005).
    private String tokenFor(UserRole role) {
        UserAccount account = accountRepository.save(new UserAccount(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
                "hashed-password", "Usuario de prueba", role));
        return tokenService.createToken(account);
    }
}
