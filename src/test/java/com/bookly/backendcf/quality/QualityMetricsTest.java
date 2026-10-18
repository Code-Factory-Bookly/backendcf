package com.bookly.backendcf.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios del record QualityMetrics y sus enum/calculos asociados.
 *
 * Cubre la logica pura del modelo (sin Spring, sin HTTP, sin archivos).
 * Estos tests ejercitan codigo real del modulo (no son stubs vacios),
 * lo que hace que JaCoCo reporte cobertura real al ejecutarlos.
 */
class QualityMetricsTest {

    // 6 requisitos de ejemplo para los tests
    private static final QualityMetrics.Requisito REQ1 = req(1, "Cobertura", "90%", QualityMetrics.Estado.PASS);
    private static final QualityMetrics.Requisito REQ2 = req(2, "Deuda",  "1 dia", QualityMetrics.Estado.PASS);
    private static final QualityMetrics.Requisito REQ3 = req(3, "Complejidad", "max 30", QualityMetrics.Estado.PASS);
    private static final QualityMetrics.Requisito REQ4 = req(4, "Severidad", "OK", QualityMetrics.Estado.PASS);
    private static final QualityMetrics.Requisito REQ5 = req(5, "Vulns", "0", QualityMetrics.Estado.PASS);

    private static QualityMetrics.Requisito req(int n, String desc, String valor,
                                                QualityMetrics.Estado estado) {
        return new QualityMetrics.Requisito(n, desc, valor, "umbral", estado, "fuente-test");
    }

    @Test
    @DisplayName("todosPasan() es true solo cuando los 5 requisitos son PASS")
    void todosPasanWhenAllPass() {
        QualityMetrics m = new QualityMetrics(REQ1, REQ2, REQ3, REQ4, REQ5,
                "fuente-test", "2026-01-01T00:00:00Z");

        assertThat(m.todosPasan()).isTrue();
        assertThat(m.cantidadPass()).isEqualTo(5);
        assertThat(m.cantidadFail()).isEqualTo(0);
    }

    @Test
    @DisplayName("todosPasan() es false si algun requisito es FAIL")
    void todosPasanWhenAnyFails() {
        QualityMetrics.Requisito fail = req(2, "Deuda", "5 dias", QualityMetrics.Estado.FAIL);
        QualityMetrics m = new QualityMetrics(REQ1, fail, REQ3, REQ4, REQ5,
                "fuente-test", "2026-01-01T00:00:00Z");

        assertThat(m.todosPasan()).isFalse();
        assertThat(m.cantidadPass()).isEqualTo(4);
        assertThat(m.cantidadFail()).isEqualTo(1);
    }

    @Test
    @DisplayName("DESCONOCIDO no cuenta como PASS ni como FAIL")
    void desconocidoNoCuentaComoNada() {
        QualityMetrics.Requisito unknown = req(2, "Deuda", "(?)", QualityMetrics.Estado.DESCONOCIDO);
        QualityMetrics m = new QualityMetrics(REQ1, unknown, REQ3, REQ4, REQ5,
                "fuente-test", "2026-01-01T00:00:00Z");

        assertThat(m.cantidadPass()).isEqualTo(4);
        assertThat(m.cantidadFail()).isEqualTo(0);
        assertThat(m.todosPasan()).isFalse();
    }

    @Test
    @DisplayName("WARN no cuenta como PASS pero tampoco como FAIL")
    void warnNoCuentaComoNada() {
        QualityMetrics.Requisito warn = req(2, "Deuda", "1.5 dias", QualityMetrics.Estado.WARN);
        QualityMetrics m = new QualityMetrics(REQ1, warn, REQ3, REQ4, REQ5,
                "fuente-test", "2026-01-01T00:00:00Z");

        assertThat(m.cantidadPass()).isEqualTo(4);
        assertThat(m.cantidadFail()).isEqualTo(0);
        assertThat(m.todosPasan()).isFalse();
    }

    @Test
    @DisplayName("Multiples FAIL se cuentan todos")
    void multiplesFailSeCuentanTodos() {
        QualityMetrics.Requisito fail1 = req(2, "Deuda", "5 dias", QualityMetrics.Estado.FAIL);
        QualityMetrics.Requisito fail2 = req(5, "Vulns", "3 abiertas", QualityMetrics.Estado.FAIL);
        QualityMetrics m = new QualityMetrics(REQ1, fail1, REQ3, REQ4, fail2,
                "fuente-test", "2026-01-01T00:00:00Z");

        assertThat(m.cantidadFail()).isEqualTo(2);
        assertThat(m.cantidadPass()).isEqualTo(3);
        assertThat(m.todosPasan()).isFalse();
    }

    @Test
    @DisplayName("unknownAll() devuelve 5 requisitos en estado DESCONOCIDO")
    void unknownAllDevuelveDesconocidos() {
        QualityMetrics m = QualityMetrics.unknownAll();

        assertThat(m.req1Cobertura().estado()).isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
        assertThat(m.req2DeudaTecnica().estado()).isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
        assertThat(m.req3Complejidad().estado()).isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
        assertThat(m.req4Severidad().estado()).isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
        assertThat(m.req5Vulnerabilidades().estado()).isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
        assertThat(m.fuentePrincipal()).contains("ninguna fuente");
        assertThat(m.timestamp()).isNotBlank();
    }

    @Test
    @DisplayName("unknownAll() genera un timestamp reciente (< 1 minuto)")
    void unknownAllGeneraTimestampReciente() {
        java.time.Instant antes = java.time.Instant.now();
        QualityMetrics m = QualityMetrics.unknownAll();
        java.time.Instant despues = java.time.Instant.now();

        java.time.OffsetDateTime ts = java.time.OffsetDateTime.parse(m.timestamp());
        assertThat(ts.toInstant()).isBetween(antes.minusSeconds(1), despues.plusSeconds(1));
    }

    @Test
    @DisplayName("Estado.simbolo() devuelve el marcador correcto para cada estado")
    void estadoSimbolo() {
        assertThat(QualityMetrics.Estado.PASS.simbolo()).isEqualTo("[OK]");
        assertThat(QualityMetrics.Estado.WARN.simbolo()).isEqualTo("[!!]");
        assertThat(QualityMetrics.Estado.FAIL.simbolo()).isEqualTo("[XX]");
        assertThat(QualityMetrics.Estado.DESCONOCIDO.simbolo()).isEqualTo("[?]");
    }

    @Test
    @DisplayName("Estado tiene exactamente 4 valores")
    void estadoTiene4Valores() {
        assertThat(QualityMetrics.Estado.values()).hasSize(4);
    }

    @Test
    @DisplayName("Requisito expone sus campos via accessors del record")
    void requisitoAccessors() {
        QualityMetrics.Requisito r = new QualityMetrics.Requisito(
                3, "desc-test", "valor-test", "umbral-test",
                QualityMetrics.Estado.PASS, "fuente-test");

        assertThat(r.numero()).isEqualTo(3);
        assertThat(r.descripcion()).isEqualTo("desc-test");
        assertThat(r.valor()).isEqualTo("valor-test");
        assertThat(r.umbral()).isEqualTo("umbral-test");
        assertThat(r.estado()).isEqualTo(QualityMetrics.Estado.PASS);
        assertThat(r.fuente()).isEqualTo("fuente-test");
    }
}