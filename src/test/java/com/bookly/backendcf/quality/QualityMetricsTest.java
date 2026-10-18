package com.bookly.backendcf.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios del record QualityMetrics y sus enum/calculos asociados.
 *
 * Cubre la logica pura del modelo (sin Spring, sin HTTP, sin archivos):
 *   - pasar() / cantidadPass() / cantidadFail() / todosPasan()
 *   - representacion textual del estado
 *   - factory unknownAll() para fallbacks
 */
class QualityMetricsTest {

    private QualityMetrics.Requisito req(int n, String desc, String valor,
                                          QualityMetrics.Estado estado) {
        return new QualityMetrics.Requisito(n, desc, valor, "", estado, "");
    }

    @Test
    @DisplayName("todosPasan() es true solo cuando los 5 requisitos son PASS")
    void todosPasanWhenAllPass() {
        QualityMetrics m = new QualityMetrics(
                req(1, "r1", "v1", QualityMetrics.Estado.PASS),
                req(2, "r2", "v2", QualityMetrics.Estado.PASS),
                req(3, "r3", "v3", QualityMetrics.Estado.PASS),
                req(4, "r4", "v4", QualityMetrics.Estado.PASS),
                req(5, "r5", "v5", QualityMetrics.Estado.PASS),
                "test", "2026-01-01T00:00:00Z");

        assertThat(m.todosPasan()).isTrue();
        assertThat(m.cantidadPass()).isEqualTo(5);
        assertThat(m.cantidadFail()).isEqualTo(0);
    }

    @Test
    @DisplayName("todosPasan() es false si algun requisito es FAIL")
    void todosPasanWhenAnyFails() {
        QualityMetrics m = new QualityMetrics(
                req(1, "r1", "v1", QualityMetrics.Estado.PASS),
                req(2, "r2", "v2", QualityMetrics.Estado.FAIL),
                req(3, "r3", "v3", QualityMetrics.Estado.PASS),
                req(4, "r4", "v4", QualityMetrics.Estado.PASS),
                req(5, "r5", "v5", QualityMetrics.Estado.PASS),
                "test", "2026-01-01T00:00:00Z");

        assertThat(m.todosPasan()).isFalse();
        assertThat(m.cantidadPass()).isEqualTo(4);
        assertThat(m.cantidadFail()).isEqualTo(1);
    }

    @Test
    @DisplayName("DESCONOCIDO no cuenta como PASS ni como FAIL")
    void desconocidoNoCuentaComoNada() {
        QualityMetrics m = new QualityMetrics(
                req(1, "r1", "v1", QualityMetrics.Estado.PASS),
                req(2, "r2", "v2", QualityMetrics.Estado.DESCONOCIDO),
                req(3, "r3", "v3", QualityMetrics.Estado.PASS),
                req(4, "r4", "v4", QualityMetrics.Estado.PASS),
                req(5, "r5", "v5", QualityMetrics.Estado.PASS),
                "test", "2026-01-01T00:00:00Z");

        assertThat(m.cantidadPass()).isEqualTo(4);
        assertThat(m.cantidadFail()).isEqualTo(0);
        assertThat(m.todosPasan()).isFalse(); // desconocido != pass
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
    @DisplayName("Estado.simbolo() devuelve el marcador correcto")
    void estadoSimbolo() {
        assertThat(QualityMetrics.Estado.PASS.simbolo()).isEqualTo("[OK]");
        assertThat(QualityMetrics.Estado.WARN.simbolo()).isEqualTo("[!!]");
        assertThat(QualityMetrics.Estado.FAIL.simbolo()).isEqualTo("[XX]");
        assertThat(QualityMetrics.Estado.DESCONOCIDO.simbolo()).isEqualTo("[?]");
    }

    @Test
    @DisplayName("Requisito expone sus campos via accessors")
    void requisitoAccessors() {
        QualityMetrics.Requisito r = new QualityMetrics.Requisito(
                3, "desc", "valor", "umbral",
                QualityMetrics.Estado.PASS, "fuente");

        assertThat(r.numero()).isEqualTo(3);
        assertThat(r.descripcion()).isEqualTo("desc");
        assertThat(r.valor()).isEqualTo("valor");
        assertThat(r.umbral()).isEqualTo("umbral");
        assertThat(r.estado()).isEqualTo(QualityMetrics.Estado.PASS);
        assertThat(r.fuente()).isEqualTo("fuente");
    }
}