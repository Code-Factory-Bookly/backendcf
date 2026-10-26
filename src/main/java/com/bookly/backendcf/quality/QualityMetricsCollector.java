package com.bookly.backendcf.quality;

/**
 * Contrato para los colectores de metricas de calidad.
 * Cada implementacion obtiene datos desde una fuente diferente
 * (Jacoco local, SonarCloud API, archivo cacheado).
 *
 * Si una fuente no esta disponible, el colector debe devolver un
 * QualityMetrics con valores DESCONOCIDO en lugar de lanzar excepcion.
 */
public interface QualityMetricsCollector {

    /**
     * Intenta colectar las 5 metricas de calidad.
     * Nunca lanza excepcion: si la fuente falla, devuelve metricas
     * con estado DESCONOCIDO.
     */
    QualityMetrics collect();

    /**
     * Nombre de la fuente (usado en el reporte).
     */
    String fuente();
}