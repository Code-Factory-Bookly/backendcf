package com.bookly.backendcf.report.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.audit.application.AuditRecorder;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Expiración por inactividad del reporte general (HU-16), de punta a punta por HTTP.
 *
 * <p>El límite real es de 15 minutos. Para no esperarlos, esta clase arranca la aplicación con
 * {@code security.report.inactivity-minutes=0}: la segunda consulta de la misma sesión, hecha un
 * instante después de la primera, ya supera la inactividad permitida. La propiedad es la misma que
 * usa producción, así que se prueba el cableado real y no un componente suelto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "security.report.inactivity-minutes=0")
@Tag("integration")
class GeneralReportSessionExpirationTest {

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
                ReportPeriod.MES, hoy.minusDays(29), hoy, 1L, 0L, 0.0, BigDecimal.TEN, List.of(), null));
    }

    @Test
    void laConsultaTrasLaInactividadSeRechazaConForbiddenYNoSeAudita() throws Exception {
        String token = tokenDeUnAdministradorConMfa();

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        esperar();

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        // Solo la primera consulta llegó al servicio y quedó auditada.
        verify(generalReportService, times(1)).generate(any());
        verify(auditRecorder, times(1)).record(any());
    }

    @Test
    void unaSesionExpiradaNoSeRecuperaReintentandoConLaMismaCredencial() throws Exception {
        String token = tokenDeUnAdministradorConMfa();
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        esperar();
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void laExpiracionDeUnAdministradorNoAfectaAOtro() throws Exception {
        String tokenExpirado = tokenDeUnAdministradorConMfa();
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + tokenExpirado)).andExpect(status().isOk());
        esperar();
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + tokenExpirado))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + tokenDeUnAdministradorConMfa()))
                .andExpect(status().isOk());
    }

    /** Deja pasar un instante para que la segunda consulta quede estrictamente después de la primera. */
    private static void esperar() throws InterruptedException {
        Thread.sleep(20);
    }

    private String tokenDeUnAdministradorConMfa() {
        UserAccount admin = accountRepository.save(new UserAccount(
                "admin-" + UUID.randomUUID() + "@example.com", "hashed-password",
                "Administrador de prueba", UserRole.ADMIN));
        return tokenService.createToken(admin, true);
    }
}
