package com.bookly.backendcf.report.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.audit.application.AuditRecorder;
import com.bookly.backendcf.audit.domain.model.AuditAction;
import com.bookly.backendcf.audit.domain.model.AuditActionType;
import com.bookly.backendcf.audit.domain.model.AuditResourceType;
import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.report.application.GeneralReportService;
import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.presentation.dto.GeneralReportResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Seguridad, contrato HTTP y auditoría del reporte general de uso (HU-16).
 *
 * <p>Cubre los escenarios "acceso con MFA" y "auditoría de cada consulta" a nivel HTTP. El servicio
 * y el registrador de auditoría se simulan para aislar lo que decide el controlador: quién puede
 * entrar, qué contrato devuelve y qué deja registrado. Que la auditoría llegue de verdad a la base
 * y sea inmutable se comprueba en {@code GeneralReportAuditIntegrationTest}.
 *
 * <p>La ruta exige la autoridad {@code REPORT_MFA_VERIFIED}, que el filtro JWT solo concede a un
 * administrador cuyo token trae el segundo factor verificado. Ser ADMIN no basta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class GeneralReportControllerSecurityTest {

    private static final String URL = "/api/v1/reportes/general";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private GeneralReportService generalReportService;

    @MockitoBean
    private AuditRecorder auditRecorder;

    @BeforeEach
    void setUp() {
        LocalDate hoy = LocalDate.now();
        when(generalReportService.generate(any())).thenReturn(new GeneralReportResponse(
                ReportPeriod.MES, hoy.minusDays(29), hoy, 12L, 3L, 0.1,
                new BigDecimal("450.00"),
                List.of(new GeneralReportResponse.ResourceUsage(
                        UUID.fromString("00000000-0000-0000-0000-000000000042"), "Camilla", 2, 5L)),
                null));
    }

    // ----------------------------------------------------------------------------------
    // Acceso: solo un administrador con el segundo factor verificado
    // ----------------------------------------------------------------------------------

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(generalReportService, never()).generate(any());
        verify(auditRecorder, never()).record(any());
    }

    @Test
    void elClienteRecibeForbiddenAunqueSuTokenTraigaElSegundoFactor() throws Exception {
        // El filtro solo concede REPORT_MFA_VERIFIED al rol ADMIN, aunque el token diga mfaVerified.
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token(cuenta(UserRole.CUSTOMER), true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verify(generalReportService, never()).generate(any());
        verify(auditRecorder, never()).record(any());
    }

    @Test
    void elProfesionalRecibeForbidden() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token(cuenta(UserRole.PROFESSIONAL), true)))
                .andExpect(status().isForbidden());

        verify(generalReportService, never()).generate(any());
    }

    @Test
    void unAdministradorSinElSegundoFactorVerificadoRecibeForbidden() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token(cuenta(UserRole.ADMIN), false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verify(generalReportService, never()).generate(any());
        verify(auditRecorder, never()).record(any());
    }

    // ----------------------------------------------------------------------------------
    // Contrato de la respuesta
    // ----------------------------------------------------------------------------------

    @Test
    void elAdministradorConMfaRecibeLasEstadisticasDelPeriodo() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token(cuenta(UserRole.ADMIN), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("MES"))
                .andExpect(jsonPath("$.from").value(LocalDate.now().minusDays(29).toString()))
                .andExpect(jsonPath("$.to").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.totalReservations").value(12))
                .andExpect(jsonPath("$.cancelledReservations").value(3))
                .andExpect(jsonPath("$.averageCancellationsPerDay").value(0.1))
                .andExpect(jsonPath("$.recognizedRevenue").value(450.00))
                .andExpect(jsonPath("$.resourceUsage[0].resourceName").value("Camilla"))
                .andExpect(jsonPath("$.resourceUsage[0].totalQuantity").value(2))
                .andExpect(jsonPath("$.resourceUsage[0].bookingsUsingResource").value(5))
                .andExpect(jsonPath("$.message").value(nullValue()));
    }

    @Test
    void sinPeriodoElServicioRecibeNuloYDecideElValorPorDefecto() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token(cuenta(UserRole.ADMIN), true)))
                .andExpect(status().isOk());

        verify(generalReportService).generate(isNull());
    }

    @Test
    void elPeriodoSemanaLlegaAlServicio() throws Exception {
        mockMvc.perform(get(URL + "?periodo=SEMANA")
                        .header("Authorization", "Bearer " + token(cuenta(UserRole.ADMIN), true)))
                .andExpect(status().isOk());

        verify(generalReportService).generate(eq(ReportPeriod.SEMANA));
    }

    @Test
    void elPeriodoEnMinusculasSeConvierte() throws Exception {
        mockMvc.perform(get(URL + "?periodo=semana")
                        .header("Authorization", "Bearer " + token(cuenta(UserRole.ADMIN), true)))
                .andExpect(status().isOk());

        verify(generalReportService).generate(eq(ReportPeriod.SEMANA));
    }

    @Test
    void unPeriodoDesconocidoDevuelveBadRequestYNoSeAudita() throws Exception {
        mockMvc.perform(get(URL + "?periodo=TRIMESTRE")
                        .header("Authorization", "Bearer " + token(cuenta(UserRole.ADMIN), true)))
                .andExpect(status().isBadRequest());

        verify(generalReportService, never()).generate(any());
        verify(auditRecorder, never()).record(any());
    }

    // ----------------------------------------------------------------------------------
    // Auditoría: cada consulta deja una acción REPORT_VIEWED
    // ----------------------------------------------------------------------------------

    @Test
    void cadaConsultaRegistraUnaAccionReportViewedConElActorElPeriodoYElRango() throws Exception {
        UserAccount admin = cuenta(UserRole.ADMIN);

        mockMvc.perform(get(URL + "?periodo=SEMANA").header("Authorization", "Bearer " + token(admin, true)))
                .andExpect(status().isOk());

        AuditAction accion = unicaAccionRegistrada();
        assertThat(accion.actorUserId()).isEqualTo(admin.getId());
        assertThat(accion.actionType()).isEqualTo(AuditActionType.REPORT_VIEWED);
        assertThat(accion.resourceType()).isEqualTo(AuditResourceType.REPORT);
        assertThat(accion.resourceId()).isNotNull();
        assertThat(accion.occurredAt()).isNotNull();
        assertThat(accion.metadata())
                .containsEntry("report", "HU-16")
                .containsEntry("period", "MES")   // el servicio simulado responde con el periodo MES
                .containsEntry("from", LocalDate.now().minusDays(29).toString())
                .containsEntry("to", LocalDate.now().toString());
    }

    @Test
    void laIpDeOrigenEsLaDeLaConexionCuandoNoHayProxy() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token(cuenta(UserRole.ADMIN), true)))
                .andExpect(status().isOk());

        assertThat(unicaAccionRegistrada().sourceIp()).isEqualTo("127.0.0.1");
    }

    @Test
    void conUnProxyLaIpEsLaUltimaDeXForwardedForYNoLaQueElClienteFalsifica() throws Exception {
        mockMvc.perform(get(URL)
                        .header("Authorization", "Bearer " + token(cuenta(UserRole.ADMIN), true))
                        .header("X-Forwarded-For", "6.6.6.6, 203.0.113.9"))
                .andExpect(status().isOk());

        // El primer salto lo escribe el cliente y se puede falsificar; el último lo agrega el proxy.
        assertThat(unicaAccionRegistrada().sourceIp()).isEqualTo("203.0.113.9");
    }

    @Test
    void dosConsultasDejanDosRegistrosSobreElMismoRecursoDelReporte() throws Exception {
        String token = token(cuenta(UserRole.ADMIN), true);

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        ArgumentCaptor<AuditAction> captor = ArgumentCaptor.forClass(AuditAction.class);
        verify(auditRecorder, times(2)).record(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
        assertThat(captor.getAllValues().get(0).resourceId())
                .isEqualTo(captor.getAllValues().get(1).resourceId());
    }

    // ----------------------------------------------------------------------------------
    // Ayudantes
    // ----------------------------------------------------------------------------------

    private AuditAction unicaAccionRegistrada() {
        ArgumentCaptor<AuditAction> captor = ArgumentCaptor.forClass(AuditAction.class);
        verify(auditRecorder).record(captor.capture());
        return captor.getValue();
    }

    // El token solo lo acepta el filtro si la cuenta existe en la base. Cada prueba crea la suya,
    // porque la sesión del reporte se identifica por el token y no debe compartirse.
    private UserAccount cuenta(UserRole role) {
        return accountRepository.save(new UserAccount(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
                "hashed-password", "Usuario de prueba", role));
    }

    private String token(UserAccount account, boolean mfaVerificado) {
        return tokenService.createToken(account, mfaVerificado);
    }
}
