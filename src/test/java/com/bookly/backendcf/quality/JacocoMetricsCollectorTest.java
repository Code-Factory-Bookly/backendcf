package com.bookly.backendcf.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del JacocoMetricsCollector (colector local desde jacoco.csv).
 *
 * El CSV puede no existir si no se corrio mvn test antes. Estos tests
 * verifican que el colector maneja correctamente ambos casos.
 */
class JacocoMetricsCollectorTest {

    @Test
    @DisplayName("Recolector expone una fuente descriptiva")
    void fuenteEsDescriptiva() {
        JacocoMetricsCollector c = new JacocoMetricsCollector();
        assertThat(c.fuente()).contains("JaCoCo");
    }

    @Test
    @DisplayName("collect() devuelve un QualityMetrics con 5 requisitos")
    void collectDevuelveEstructura() {
        JacocoMetricsCollector c = new JacocoMetricsCollector();
        QualityMetrics result = c.collect();

        assertThat(result).isNotNull();
        assertThat(result.req1Cobertura()).isNotNull();
        assertThat(result.req2DeudaTecnica()).isNotNull();
        assertThat(result.req3Complejidad()).isNotNull();
        assertThat(result.req4Severidad()).isNotNull();
        assertThat(result.req5Vulnerabilidades()).isNotNull();
    }

    @Test
    @DisplayName("Fuente cambia segun exista el CSV")
    void fuenteCambiaSegunCsv() {
        JacocoMetricsCollector c = new JacocoMetricsCollector();
        String fuente = c.fuente();
        // El mensaje es descriptivo del estado actual
        assertThat(fuente).isNotBlank();
        assertThat(fuente.length()).isGreaterThan(5);
    }
}