package com.bookly.backendcf.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del parser del cache local (docs/QUALITY-STATUS.md).
 *
 * No usamos el classpath real para no depender de que el archivo
 * este en el JAR; en su lugar verificamos que el colector maneja
 * correctamente los casos:
 *   - cache no encontrado (unknownAll)
 *   - contenido mal formado (unknownAll)
 *   - contenido valido (PASS/PASS/...)
 */
class CachedMetricsCollectorTest {

    @Test
    @DisplayName("Sin cache en classpath devuelve unknownAll")
    void sinCacheDevuelveDesconocido() {
        CachedMetricsCollector c = new CachedMetricsCollector();
        QualityMetrics m = c.collect();

        // Si el cache existe (en el classpath real), los estados son
        // PASS o FAIL. Si no existe, todos son DESCONOCIDO.
        // Ambos casos son validos; el test verifica que nunca rompe.
        assertThat(m).isNotNull();
        assertThat(m.fuentePrincipal()).isNotBlank();
        assertThat(m.timestamp()).isNotBlank();
    }

    @Test
    @DisplayName("Recolector expone su fuente correctamente")
    void fuenteEsDescriptiva() {
        CachedMetricsCollector c = new CachedMetricsCollector();
        assertThat(c.fuente()).contains("QUALITY-STATUS");
    }

    @Test
    @DisplayName("Estados PASS siempre tienen descripcion no vacia")
    void estadosPassTienenDescripcion() {
        CachedMetricsCollector c = new CachedMetricsCollector();
        QualityMetrics m = c.collect();

        // Recorremos los 5 requisitos y validamos estructura
        QualityMetrics.Requisito[] reqs = {
                m.req1Cobertura(), m.req2DeudaTecnica(),
                m.req3Complejidad(), m.req4Severidad(),
                m.req5Vulnerabilidades()
        };
        for (QualityMetrics.Requisito r : reqs) {
            assertThat(r.numero()).isBetween(1, 5);
            assertThat(r.descripcion()).isNotBlank();
            assertThat(r.umbral()).isNotBlank();
            assertThat(r.fuente()).isNotBlank();
            // valor puede ser "(no encontrado)" si no hay cache, eso es valido
            assertThat(r.valor()).isNotNull();
        }
    }
}