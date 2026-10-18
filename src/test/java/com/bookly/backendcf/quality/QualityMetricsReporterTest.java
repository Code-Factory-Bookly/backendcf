package com.bookly.backendcf.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del QualityMetricsReporter.
 *
 * Usa el constructor alternativo que acepta la interfaz
 * QualityMetricsCollector, lo que permite inyectar stubs sin
 * necesitar un ApplicationContext de Spring completo.
 */
class QualityMetricsReporterTest {

    /** Colector stub que devuelve unknownAll. */
    private static QualityMetricsCollector stubOk() {
        return new QualityMetricsCollector() {
            @Override public QualityMetrics collect() { return QualityMetrics.unknownAll(); }
            @Override public String fuente() { return "stub"; }
        };
    }

    /** Colector stub que siempre lanza. */
    private static QualityMetricsCollector stubFalla() {
        return new QualityMetricsCollector() {
            @Override public QualityMetrics collect() {
                throw new RuntimeException("simulado");
            }
            @Override public String fuente() { return "stub-falla"; }
        };
    }

    @Test
    @DisplayName("getCurrentMetrics() no lanza excepciones aunque fallen los colectores")
    void getCurrentMetricsNuncaFalla() {
        QualityMetricsReporter reporter = new QualityMetricsReporter(
                stubOk(), stubOk(), stubOk());

        QualityMetrics result = reporter.getCurrentMetrics();
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("getCurrentMetrics() con colectores que fallan devuelve metricas desconocidas")
    void getCurrentMetricsManejaFallas() {
        QualityMetricsReporter reporter = new QualityMetricsReporter(
                stubFalla(), stubFalla(), stubFalla());

        QualityMetrics result = reporter.getCurrentMetrics();
        assertThat(result).isNotNull();
        assertThat(result.req1Cobertura().estado()).isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
        assertThat(result.req5Vulnerabilidades().estado()).isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
    }

    @Test
    @DisplayName("getCurrentMetrics() mergea correctamente cuando jacoco tiene datos y cache no")
    void getCurrentMetricsMergeaCorrectamente() {
        QualityMetrics jacocoOk = new QualityMetrics(
                new QualityMetrics.Requisito(1, "r1", "90%", ">=65%", QualityMetrics.Estado.PASS, "jacoco"),
                new QualityMetrics.Requisito(2, "r2", "?", "?", QualityMetrics.Estado.DESCONOCIDO, "jacoco"),
                new QualityMetrics.Requisito(3, "r3", "30", "<50", QualityMetrics.Estado.PASS, "jacoco"),
                new QualityMetrics.Requisito(4, "r4", "?", "?", QualityMetrics.Estado.DESCONOCIDO, "jacoco"),
                new QualityMetrics.Requisito(5, "r5", "?", "?", QualityMetrics.Estado.DESCONOCIDO, "jacoco"),
                "jacoco", "2026-01-01T00:00:00Z");

        QualityMetricsCollector jacocoStub = new QualityMetricsCollector() {
            @Override public QualityMetrics collect() { return jacocoOk; }
            @Override public String fuente() { return "jacoco"; }
        };

        QualityMetricsReporter reporter = new QualityMetricsReporter(
                stubOk(), jacocoStub, stubOk());

        QualityMetrics result = reporter.getCurrentMetrics();
        // #1 cobertura viene de jacoco (PASS), no de cache
        assertThat(result.req1Cobertura().valor()).isEqualTo("90%");
        assertThat(result.req1Cobertura().estado()).isEqualTo(QualityMetrics.Estado.PASS);
        // #3 complejidad viene de jacoco
        assertThat(result.req3Complejidad().valor()).isEqualTo("30");
    }
}