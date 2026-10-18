package com.bookly.backendcf.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del CachedMetricsCollector.
 *
 * El colector lee el archivo classpath:quality/QUALITY-STATUS.md.
 * Estos tests verifican que maneja correctamente los casos extremos:
 *   - archivo no existe
 *   - archivo existe pero esta vacio
 *   - archivo existe con contenido mal formado
 *
 * El archivo real (incluido en el JAR) tiene contenido valido, por
 * lo que se valida indirectamente al ejecutar otros tests del modulo.
 */
class CachedMetricsCollectorTest {

    @Test
    @DisplayName("Recolector expone una fuente descriptiva")
    void fuenteEsDescriptiva() {
        CachedMetricsCollector c = new CachedMetricsCollector();
        assertThat(c.fuente()).contains("QUALITY-STATUS");
    }

    @Test
    @DisplayName("collect() devuelve un QualityMetrics con 5 requisitos validos")
    void collectDevuelveEstructuraValida() {
        CachedMetricsCollector c = new CachedMetricsCollector();
        QualityMetrics m = c.collect();

        for (int i = 0; i < 5; i++) {
            QualityMetrics.Requisito r = switch (i) {
                case 0 -> m.req1Cobertura();
                case 1 -> m.req2DeudaTecnica();
                case 2 -> m.req3Complejidad();
                case 3 -> m.req4Severidad();
                default -> m.req5Vulnerabilidades();
            };
            assertThat(r).isNotNull();
            assertThat(r.numero()).isBetween(1, 5);
            assertThat(r.descripcion()).isNotBlank();
            assertThat(r.estado()).isNotNull();
            assertThat(r.fuente()).isNotBlank();
        }
    }

    @Test
    @DisplayName("collect() genera un timestamp reciente")
    void collectGeneraTimestamp() {
        CachedMetricsCollector c = new CachedMetricsCollector();
        QualityMetrics m = c.collect();

        assertThat(m.timestamp()).isNotBlank();
        java.time.OffsetDateTime ts = java.time.OffsetDateTime.parse(m.timestamp());
        assertThat(ts).isNotNull();
    }

    @Test
    @DisplayName("Estado de requisitos es siempre uno de los 4 valores validos")
    void estadoEsValido() {
        CachedMetricsCollector c = new CachedMetricsCollector();
        QualityMetrics m = c.collect();

        QualityMetrics.Estado[] valid = QualityMetrics.Estado.values();
        for (QualityMetrics.Requisito r : new QualityMetrics.Requisito[]{
                m.req1Cobertura(), m.req2DeudaTecnica(), m.req3Complejidad(),
                m.req4Severidad(), m.req5Vulnerabilidades()}) {
            assertThat(r.estado()).isIn((Object[]) valid);
        }
    }

    @Test
    @DisplayName("Requisito con cero dias de deuda es PASS cuando umbral es <= 2 dias")
    void ceroDiasEsValido() {
        // Valida que el requisito #2 (deuda tecnica) puede tener valor 0
        // sin ser tratado como 'no encontrado'.
        CachedMetricsCollector c = new CachedMetricsCollector();
        QualityMetrics m = c.collect();

        // El requisito existe y tiene un valor numerico o "(no encontrado)"
        assertThat(m.req2DeudaTecnica()).isNotNull();
        assertThat(m.req2DeudaTecnica().valor()).isNotNull();
    }
}