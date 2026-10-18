package com.bookly.backendcf.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del SonarCloudMetricsCollector.
 *
 * Sin SONAR_TOKEN configurado, collect() devuelve metricas con
 * estado DESCONOCIDO (sin intentar la API). Esto valida que el
 * colector es seguro de instanciar y llamar en cualquier entorno.
 */
class SonarCloudMetricsCollectorTest {

    @Test
    @DisplayName("Sin token, fuente dice 'sin token'")
    void sinTokenFuenteEsDescriptiva() {
        SonarCloudMetricsCollector c = new SonarCloudMetricsCollector("");
        assertThat(c.fuente()).contains("sin token");
    }

    @Test
    @DisplayName("Sin token, collect devuelve metricas con estado DESCONOCIDO")
    void sinTokenCollectDevuelveDesconocido() {
        SonarCloudMetricsCollector c = new SonarCloudMetricsCollector("");
        QualityMetrics result = c.collect();

        assertThat(result).isNotNull();
        assertThat(result.req1Cobertura().estado()).isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
        assertThat(result.req2DeudaTecnica().estado()).isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
        assertThat(result.req3Complejidad().estado()).isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
        assertThat(result.req4Severidad().estado()).isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
        assertThat(result.req5Vulnerabilidades().estado()).isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
        assertThat(result.fuentePrincipal()).contains("SONAR_TOKEN");
    }

    @Test
    @DisplayName("Con token vacio, se maneja como sin token")
    void tokenVacioEsComoSinToken() {
        SonarCloudMetricsCollector c = new SonarCloudMetricsCollector("");
        assertThat(c.collect().req1Cobertura().estado())
                .isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
    }
}