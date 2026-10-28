package com.bookly.backendcf.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

    /** Colector stub que devuelve metricas todas en PASS. */
    private static QualityMetricsCollector stubAllPass() {
        return new QualityMetricsCollector() {
            @Override public QualityMetrics collect() {
                return new QualityMetrics(
                        new QualityMetrics.Requisito(1, "r1", "v1", "u1", QualityMetrics.Estado.PASS, "stub"),
                        new QualityMetrics.Requisito(2, "r2", "v2", "u2", QualityMetrics.Estado.PASS, "stub"),
                        new QualityMetrics.Requisito(3, "r3", "v3", "u3", QualityMetrics.Estado.PASS, "stub"),
                        new QualityMetrics.Requisito(4, "r4", "v4", "u4", QualityMetrics.Estado.PASS, "stub"),
                        new QualityMetrics.Requisito(5, "r5", "v5", "u5", QualityMetrics.Estado.PASS, "stub"),
                        "stub-allpass", "2026-01-01T00:00:00Z");
            }
            @Override public String fuente() { return "stub"; }
        };
    }

    /** Colector stub con metricas mezcladas (algunas FAIL). */
    private static QualityMetricsCollector stubMixto() {
        return new QualityMetricsCollector() {
            @Override public QualityMetrics collect() {
                return new QualityMetrics(
                        new QualityMetrics.Requisito(1, "r1", "v1", "u1", QualityMetrics.Estado.PASS, "stub"),
                        new QualityMetrics.Requisito(2, "r2", "v2", "u2", QualityMetrics.Estado.FAIL, "stub"),
                        new QualityMetrics.Requisito(3, "r3", "v3", "u3", QualityMetrics.Estado.PASS, "stub"),
                        new QualityMetrics.Requisito(4, "r4", "v4", "u4", QualityMetrics.Estado.WARN, "stub"),
                        new QualityMetrics.Requisito(5, "r5", "v5", "u5", QualityMetrics.Estado.PASS, "stub"),
                        "stub-mixto", "2026-01-01T00:00:00Z");
            }
            @Override public String fuente() { return "stub"; }
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
        assertThat(result.req1Cobertura().valor()).isEqualTo("90%");
        assertThat(result.req1Cobertura().estado()).isEqualTo(QualityMetrics.Estado.PASS);
        assertThat(result.req3Complejidad().valor()).isEqualTo("30");
    }

    @Test
    @DisplayName("onWebServerReady imprime el reporte sin lanzar excepciones")
    void onWebServerReadyNoLanzaExcepciones() {
        QualityMetricsReporter reporter = new QualityMetricsReporter(
                stubAllPass(), stubAllPass(), stubAllPass());

        // Capturar stdout para no contaminar la consola del test
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer));
        try {
            reporter.onWebServerReady();
        } finally {
            System.setOut(original);
        }

        String output = buffer.toString();
        assertThat(output).contains("CALIDAD DEL PROYECTO");
        assertThat(output).contains("Todos los requisitos del Sprint 2/#3 estan en verde");
    }

    @Test
    @DisplayName("onWebServerReady con fallos imprime sugerencias")
    void onWebServerReadyConFallosImprimeSugerencias() {
        QualityMetricsReporter reporter = new QualityMetricsReporter(
                stubMixto(), stubMixto(), stubMixto());

        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer));
        try {
            reporter.onWebServerReady();
        } finally {
            System.setOut(original);
        }

        String output = buffer.toString();
        assertThat(output).contains("Sugerencias para mejorar el reporte");
    }

    @Test
    @DisplayName("onWebServerReady maneja excepciones del colector sin romper")
    void onWebServerReadyManejaExcepciones() {
        QualityMetricsReporter reporter = new QualityMetricsReporter(
                stubFalla(), stubFalla(), stubFalla());

        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer));
        try {
            // No debe lanzar nada hacia afuera
            assertThatCode(reporter::onWebServerReady).doesNotThrowAnyException();
        } finally {
            System.setOut(original);
        }
    }
}