package com.bookly.backendcf.quality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reporter de calidad que imprime los 5 requisitos del Sprint 2 / #3
 * al final del arranque del backend.
 *
 * Caracteristicas clave:
 *   - TOTALMENTE DEFENSIVO: nunca lanza excepciones que rompan el arranque.
 *   - NO requiere SONAR_TOKEN para funcionar (usa fallback al cache local).
 *   - SI requiere SONAR_TOKEN para datos en vivo.
 *
 * Orden de prioridad de fuentes:
 *   1. SonarCloud en vivo (si SONAR_TOKEN esta configurado)
 *   2. JaCoCo local (si el reporte CSV existe en target/site/jacoco/)
 *   3. Cache local desde docs/QUALITY-STATUS.md (siempre presente)
 *
 * Por requisito individual:
 *   - #1 Cobertura y #3 Complejidad: prefiere JaCoCo (mas fresco)
 *   - #2 Deuda, #4 Severidad y #5 Vulnerabilidades: prefiere SonarCloud
 *     en vivo; si no hay, usa el cache.
 *
 * El reporte se dispara DESPUES de que el servidor web esta listo
 * (evento WebServerInitializedEvent), para que sea lo ULTIMO que aparece
 * en consola.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class QualityMetricsReporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QualityMetricsReporter.class);

    private final SonarCloudMetricsCollector sonar;
    private final JacocoMetricsCollector jacoco;
    private final CachedMetricsCollector cached;

    public QualityMetricsReporter(
            SonarCloudMetricsCollector sonar,
            JacocoMetricsCollector jacoco,
            CachedMetricsCollector cached
    ) {
        this.sonar = sonar;
        this.jacoco = jacoco;
        this.cached = cached;
    }

    /**
     * Se dispara cuando el servidor web esta listo (Tomcat arriba).
     * El reporte aparece AL FINAL de la consola, despues del
     * "Started BackendcfApplication in X seconds".
     *
     * Si algo falla, se loguea WARN pero NO rompe el arranque.
     */
    @EventListener(WebServerInitializedEvent.class)
    public void onWebServerReady() {
        try {
            printReport();
        } catch (Exception e) {
            log.warn("No se pudo imprimir el reporte de calidad: {}", e.toString());
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        // No-op: el reporte se imprime cuando el web server esta listo.
    }

    private void printReport() {
        QualityMetrics metrics = collectBest();

        // Render con bordes ASCII
        int width = 70;
        String border = repeat("=", width);
        String sep = repeat("-", width);

        log.info("");
        log.info(border);
        log.info("  CALIDAD DEL PROYECTO - SPRINT 2 / #3 (requisitos exigidos)");
        log.info(border);
        log.info("");
        log.info("  Fuente principal : {}", metrics.fuentePrincipal());
        log.info("  Timestamp        : {}", metrics.timestamp());
        log.info("");
        log.info("  #   REQUISITO                                          VALOR                              UMBRAL        ESTADO");
        log.info("  {}", sep);
        log.info(formatRow(metrics.req1Cobertura()));
        log.info(formatRow(metrics.req2DeudaTecnica()));
        log.info(formatRow(metrics.req3Complejidad()));
        log.info(formatRow(metrics.req4Severidad()));
        log.info(formatRow(metrics.req5Vulnerabilidades()));
        log.info("  {}", sep);
        log.info("");

        long pass = metrics.cantidadPass();
        long fail = metrics.cantidadFail();
        String verdict = metrics.todosPasan() ? "OK" : (fail > 0 ? "FALLA" : "PARCIAL");
        log.info("  Resumen: {} / 5 requisitos pasan  |  {}", pass, verdict);
        log.info("");

        if (!metrics.todosPasan()) {
            log.warn("  Hay requisitos que NO cumplen. Sugerencias:");
            log.warn("    - Cobertura: correr 'mvn test' antes de arrancar (genera target/site/jacoco/jacoco.csv)");
            log.warn("    - Deuda/Vulnerabilidades: configurar SONAR_TOKEN y correr 'mvn sonar:sonar'");
            log.warn("    - Cache local: ver docs/QUALITY-STATUS.md");
        } else {
            log.info("  Todos los requisitos del Sprint 2/#3 estan en verde.");
        }
        log.info(border);
        log.info("");
    }

    private String formatRow(QualityMetrics.Requisito r) {
        if (r == null) {
            return String.format("  %d   %-50s %-33s %-13s %s",
                    0, "(sin dato)", "(sin dato)", "-", "[?]");
        }
        String desc = r.descripcion();
        if (desc.length() > 50) desc = desc.substring(0, 47) + "...";
        String valor = r.valor();
        if (valor == null) valor = "(sin dato)";
        if (valor.length() > 33) valor = valor.substring(0, 30) + "...";
        String umbral = r.umbral();
        if (umbral == null) umbral = "-";
        return String.format("  %d   %-50s %-33s %-13s %s",
                r.numero(), desc, valor, umbral, r.estado().simbolo());
    }

    /**
     * Estrategia de recoleccion: cada requisito se toma de la mejor
     * fuente disponible. Si todo falla, devuelve metricas con
     * DESCONOCIDO pero NUNCA lanza excepciones.
     */
    private QualityMetrics collectBest() {
        QualityMetrics sonarM = safeCollect(sonar);
        QualityMetrics jacocoM = safeCollect(jacoco);
        QualityMetrics cachedM = safeCollect(cached);

        // Construir metricas "merged" requisito por requisito
        QualityMetrics.Requisito r1 = pick(
                "Cobertura de pruebas unitarias >= 65%",
                jacocoM != null ? jacocoM.req1Cobertura() : null,
                sonarM != null ? sonarM.req1Cobertura() : null,
                cachedM != null ? cachedM.req1Cobertura() : null,
                ">= 65%");
        QualityMetrics.Requisito r2 = pick(
                "Deuda tecnica <= 2 dias",
                sonarM != null ? sonarM.req2DeudaTecnica() : null,
                cachedM != null ? cachedM.req2DeudaTecnica() : null,
                null,
                "<= 2 dias");
        QualityMetrics.Requisito r3 = pick(
                "Complejidad ciclomatica < 50 (por clase)",
                jacocoM != null ? jacocoM.req3Complejidad() : null,
                sonarM != null ? sonarM.req3Complejidad() : null,
                cachedM != null ? cachedM.req3Complejidad() : null,
                "< 50");
        QualityMetrics.Requisito r4 = pick(
                "Severidad hallazgos aceptable",
                sonarM != null ? sonarM.req4Severidad() : null,
                cachedM != null ? cachedM.req4Severidad() : null,
                null,
                "OK / sin blockers");
        QualityMetrics.Requisito r5 = pick(
                "Cero vulnerabilidades criticas",
                sonarM != null ? sonarM.req5Vulnerabilidades() : null,
                cachedM != null ? cachedM.req5Vulnerabilidades() : null,
                null,
                "= 0");

        String fuente = describeFuente(sonarM, jacocoM, cachedM);

        return new QualityMetrics(r1, r2, r3, r4, r5, fuente,
                java.time.OffsetDateTime.now().toString());
    }

    /**
     * Llama al colector capturando cualquier excepcion.
     * Si falla, devuelve null (no rompe el flujo).
     */
    private QualityMetrics safeCollect(QualityMetricsCollector c) {
        if (c == null) return null;
        try {
            QualityMetrics m = c.collect();
            // Validar que la respuesta tiene requisitos reales
            if (m == null
                    || m.req1Cobertura() == null
                    || m.req2DeudaTecnica() == null) {
                return null;
            }
            return m;
        } catch (Throwable t) {
            log.debug("Colector {} fallo: {}", c.fuente(), t.toString());
            return null;
        }
    }

    /**
     * Describe que fuente se uso principalmente.
     */
    private String describeFuente(QualityMetrics sonarM, QualityMetrics jacocoM, QualityMetrics cachedM) {
        boolean sonarOk = sonarM != null
                && sonarM.req1Cobertura() != null
                && sonarM.req1Cobertura().estado() != QualityMetrics.Estado.DESCONOCIDO
                && sonarM.fuentePrincipal() != null
                && !sonarM.fuentePrincipal().contains("no disponible");
        boolean jacocoOk = jacocoM != null
                && jacocoM.req1Cobertura() != null
                && jacocoM.req1Cobertura().estado() != QualityMetrics.Estado.DESCONOCIDO;

        if (sonarOk && jacocoOk) {
            return "SonarCloud en vivo (deuda/vulns) + JaCoCo local (cobertura/complejidad)";
        } else if (sonarOk) {
            return "SonarCloud en vivo (todas las metricas)";
        } else if (jacocoOk) {
            return "JaCoCo local (cobertura/complejidad) + cache docs/QUALITY-STATUS.md";
        } else {
            return "cache docs/QUALITY-STATUS.md (sin SONAR_TOKEN ni JaCoCo local)";
        }
    }

    /**
     * Toma el primer requisito cuyo estado NO sea DESCONOCIDO.
     * Si todos son null o DESCONOCIDO, devuelve un requisito "DESCONOCIDO"
     * que NUNCA es null.
     */
    private QualityMetrics.Requisito pick(String descripcion,
                                          QualityMetrics.Requisito primary,
                                          QualityMetrics.Requisito secondary,
                                          QualityMetrics.Requisito tertiary,
                                          String umbralFallback) {
        // Construir manualmente para evitar List.of() con nulls
        List<QualityMetrics.Requisito> candidates = new ArrayList<>(3);
        if (primary != null) candidates.add(primary);
        if (secondary != null) candidates.add(secondary);
        if (tertiary != null) candidates.add(tertiary);

        for (QualityMetrics.Requisito r : candidates) {
            if (r.estado() != QualityMetrics.Estado.DESCONOCIDO) {
                return r;
            }
        }
        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }
        // Fallback absoluto: nunca devolvemos null
        return new QualityMetrics.Requisito(
                0, descripcion, "(sin dato)", umbralFallback,
                QualityMetrics.Estado.DESCONOCIDO, "ninguna fuente disponible");
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    /**
     * Metodo publico de utilidad para que otros componentes (por ejemplo,
     * un endpoint /health o /quality) puedan obtener el estado actual
     * sin lanzar excepciones.
     */
    public QualityMetrics getCurrentMetrics() {
        try {
            return collectBest();
        } catch (Exception e) {
            log.warn("Error recolectando metricas: {}", e.toString());
            return QualityMetrics.unknownAll();
        }
    }

    /**
     * Eliminado: vea {@link QualityMetrics#unknownAll}.
     * Metodo estatico eliminado porque ya no es necesario.
     */
}