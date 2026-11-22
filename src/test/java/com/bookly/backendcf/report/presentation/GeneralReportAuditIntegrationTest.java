package com.bookly.backendcf.report.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.report.application.ReportSessionActivity;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Auditoría inmutable del reporte general (HU-16), escenario "cada consulta queda registrada",
 * contra PostgreSQL real y sin simular nada: filtro JWT, controlador, registrador de auditoría,
 * migración V67 y los disparadores de la tabla.
 *
 * <p><b>Datos que quedan en la base.</b> {@code audit_log} es append-only y conserva la clave
 * foránea del actor, así que los administradores que crea esta clase no se pueden borrar. Es la
 * misma convención que siguen las pruebas de concurrencia de reservas. Cada ejecución usa un
 * correo único y no afecta a otras pruebas.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/bookly_test",
        "spring.datasource.username=bookly_test",
        "spring.datasource.password=bookly_test_local_2026",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "security.jwt.secret=BooklyIntegrationTestOnlySecretWithMoreThanSixtyFourCharacters123456789"
})
@AutoConfigureMockMvc
@Tag("integration")
class GeneralReportAuditIntegrationTest {

    private static final String URL = "/api/v1/reportes/general";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private ReportSessionActivity sessionActivity;

    @Test
    void cadaConsultaQuedaRegistradaEnLaBaseConActorIpYPeriodoConsultado() throws Exception {
        UserAccount admin = nuevoAdmin();
        String token = tokenService.createToken(admin, true);

        mockMvc.perform(get(URL + "?periodo=SEMANA")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", "6.6.6.6, 203.0.113.9"))
                .andExpect(status().isOk());
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT action_type, resource_type, resource_id, occurred_at, source_ip, "
                        + "metadata->>'report' AS reporte, metadata->>'period' AS periodo, "
                        + "metadata->>'from' AS desde, metadata->>'to' AS hasta "
                        + "FROM audit_log WHERE actor_user_id = ? ORDER BY occurred_at", admin.getId());

        assertThat(filas).as("Una fila por consulta").hasSize(2);
        Map<String, Object> semana = filas.get(0);
        assertThat(semana.get("action_type")).isEqualTo("REPORT_VIEWED");
        assertThat(semana.get("resource_type")).isEqualTo("REPORT");
        assertThat(semana.get("occurred_at")).isNotNull();
        assertThat(semana.get("source_ip")).isEqualTo("203.0.113.9");
        assertThat(semana.get("reporte")).isEqualTo("HU-16");
        assertThat(semana.get("periodo")).isEqualTo("SEMANA");
        assertThat(semana.get("desde")).isEqualTo(LocalDate.now().minusDays(6).toString());
        assertThat(semana.get("hasta")).isEqualTo(LocalDate.now().toString());

        Map<String, Object> mes = filas.get(1);
        assertThat(mes.get("periodo")).as("Sin parámetro, el periodo por defecto es MES").isEqualTo("MES");
        assertThat(mes.get("desde")).isEqualTo(LocalDate.now().minusDays(29).toString());
        assertThat(mes.get("source_ip")).isEqualTo("127.0.0.1");
        assertThat(mes.get("resource_id")).as("Ambas consultas apuntan al mismo recurso del reporte")
                .isEqualTo(semana.get("resource_id"));
    }

    @Test
    void unaConsultaRechazadaNoDejaRegistro() throws Exception {
        UserAccount adminSinMfa = nuevoAdmin();

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + tokenService.createToken(adminSinMfa, false)))
                .andExpect(status().isForbidden());

        Long filas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE actor_user_id = ?", Long.class, adminSinMfa.getId());
        assertThat(filas).isZero();
    }

    @Test
    void laBaseRechazaModificarOBorrarLosRegistrosDeAuditoria() throws Exception {
        UserAccount admin = nuevoAdmin();
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + tokenService.createToken(admin, true)))
                .andExpect(status().isOk());

        // Spring envuelve el error de PostgreSQL: el mensaje del disparador está en la causa.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE audit_log SET source_ip = '9.9.9.9' WHERE actor_user_id = ?", admin.getId()))
                .hasStackTraceContaining("audit_log is append-only");
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM audit_log WHERE actor_user_id = ?", admin.getId()))
                .hasStackTraceContaining("audit_log is append-only");

        Long filas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE actor_user_id = ? AND source_ip = '127.0.0.1'",
                Long.class, admin.getId());
        assertThat(filas).as("El registro sigue intacto tras los intentos").isEqualTo(1L);
    }

    @Test
    void elLimiteDeInactividadPorDefectoEsDeQuinceMinutos() {
        // Valor por defecto del componente (@Value de security.report.inactivity-minutes): es el que rige
        // cuando nadie define la propiedad ni la variable REPORT_INACTIVITY_MINUTES.
        assertThat((Duration) ReflectionTestUtils.getField(sessionActivity, "inactivity"))
                .isEqualTo(Duration.ofMinutes(15));
    }

    private UserAccount nuevoAdmin() {
        return accountRepository.save(new UserAccount(
                "hu16-audit-" + UUID.randomUUID() + "@example.test", "hashed-password",
                "Administrador de auditoria", UserRole.ADMIN));
    }
}
