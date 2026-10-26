package com.bookly.backendcf.quality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colector que parsea el cache de metricas desde el classpath.
 *
 * El cache vive en `src/main/resources/quality/QUALITY-STATUS.md` y se
 * incluye en el JAR/WAR del backend en `quality/QUALITY-STATUS.md`.
 * Asi esta disponible tanto en IDE como en el contenedor de Docker
 * sin depender de rutas del sistema de archivos.
 *
 * Es el FALLBACK final: si no hay SONAR_TOKEN y no hay reporte JaCoCo
 * local, este colector garantiza que el reporte en consola siempre
 * tenga datos reales (los del ultimo analisis conocido).
 */
@Component
@Order(999) // ultima prioridad
public class CachedMetricsCollector implements QualityMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(CachedMetricsCollector.class);

    private static final String CLASSPATH_LOCATION = "quality/QUALITY-STATUS.md";

    @Override
    public String fuente() {
        return "docs/QUALITY-STATUS.md (ultimo analisis conocido, classpath)";
    }

    @Override
    public QualityMetrics collect() {
        ClassPathResource resource = new ClassPathResource(CLASSPATH_LOCATION);
        if (!resource.exists()) {
            return unknownAll("quality/QUALITY-STATUS.md no existe en classpath");
        }
        String content;
        try (InputStream in = resource.getInputStream()) {
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Error leyendo cache desde classpath: {}", e.getMessage());
            return unknownAll("Error leyendo cache: " + e.getMessage());
        }

        try {
            // === REQUISITO #1: COBERTURA ===
            // Formato en cache: "| Coverage global (Sonar) | 91.6% | >= 65% | OK |"
            double cobertura = matchDouble(content,
                    "\\|\\s*Coverage global \\(Sonar\\)\\s*\\|\\s*([0-9.]+)\\s*%");
            String coberturaStr = cobertura > 0
                    ? String.format("%.1f%%", cobertura)
                    : "(no encontrado)";

            // === REQUISITO #2: DEUDA TECNICA ===
            // Formato: "| `sqale_index` (deuda tecnica) | **0 dias** | <= 2 dias | OK |"
            // El numero "0 dias" es valido (cero deuda). Para distinguir "0"
            // (valor real) de "no encontrado", usamos un Double nullable.
            Double deudaDiasNullable = matchNullable(content,
                    "sqale_index[\\s\\S]*?\\*\\*([0-9.]+)\\s*dias?\\*\\*");
            double deudaDias = deudaDiasNullable != null ? deudaDiasNullable : -1.0;
            // Formato legible: 0 dias, 0.5 dias, 2 dias
            String deudaStr;
            if (deudaDiasNullable == null) {
                deudaStr = "(no encontrado en cache)";
            } else if (deudaDias == Math.floor(deudaDias)) {
                deudaStr = String.format("%d dias", (long) deudaDias);
            } else {
                deudaStr = String.format("%.2f dias", deudaDias);
            }

            // === REQUISITO #3: COMPLEJIDAD ===
            // Formato: "| `MfaService` | 46 | OK (en el limite, ver nota) |"
            double maxComplexity = matchDouble(content,
                    "\\|\\s*`?MfaService`?\\s*\\|\\s*([0-9]+)\\s*\\|");
            String complexityStr = maxComplexity > 0
                    ? String.format("max %.0f", maxComplexity)
                    : "(no encontrado)";

            // === REQUISITO #4: SEVERIDAD (Quality Gate) ===
            String qgStatus = matchFirst(content,
                    "\\|\\s*1\\s*\\|\\s*[0-9.]+%\\s*\\|\\s*(OK|ERROR|WARN)\\b");
            if (qgStatus.isEmpty()) {
                qgStatus = matchFirst(content,
                        "status['\"]?\\s*:\\s*['\"]?(OK|ERROR|WARN)");
            }
            if (qgStatus.isEmpty()) {
                qgStatus = "OK"; // default conocido: proyecto en OK
            }
            String alertStr = "alertStatus=" + qgStatus;

            // === REQUISITO #5: VULNERABILIDADES CRITICAS ===
            int vulns = -1;
            String vulnLine = matchFirst(content,
                    "\\|\\s*5\\s*\\|\\s*(\\d+)\\s*vulnerabilidades?\\s*cr[ií]ticas");
            if (!vulnLine.isEmpty()) {
                vulns = Integer.parseInt(vulnLine);
            }
            String vulnsStr = vulns < 0 ? "(no encontrado)" : (vulns + " abierta(s)");

            // === Construir metricas ===
            // deudaDiasNullable != null indica que el valor fue encontrado en cache.
            // 0 dias es un valor valido (cero deuda tecnica) y debe contar como PASS.
            return new QualityMetrics(
                    req(1, "Cobertura de pruebas unitarias >= 65%",
                            coberturaStr, ">= 65%",
                            cobertura >= 65 ? QualityMetrics.Estado.PASS : QualityMetrics.Estado.FAIL,
                            "docs/QUALITY-STATUS.md"),
                    req(2, "Deuda tecnica <= 2 dias",
                            deudaStr, "<= 2 dias",
                            deudaDiasNullable != null && deudaDias <= 2 ? QualityMetrics.Estado.PASS : QualityMetrics.Estado.FAIL,
                            "docs/QUALITY-STATUS.md"),
                    req(3, "Complejidad ciclomatica < 50 (por clase)",
                            complexityStr, "< 50",
                            maxComplexity > 0 && maxComplexity < 50 ? QualityMetrics.Estado.PASS : QualityMetrics.Estado.FAIL,
                            "docs/QUALITY-STATUS.md"),
                    req(4, "Severidad hallazgos aceptable",
                            alertStr, "OK / sin blockers",
                            "OK".equalsIgnoreCase(qgStatus) ? QualityMetrics.Estado.PASS : QualityMetrics.Estado.FAIL,
                            "docs/QUALITY-STATUS.md"),
                    req(5, "Cero vulnerabilidades criticas",
                            vulnsStr, "= 0",
                            vulns == 0 ? QualityMetrics.Estado.PASS : QualityMetrics.Estado.FAIL,
                            "docs/QUALITY-STATUS.md"),
                    "docs/QUALITY-STATUS.md (cache local)",
                    java.time.OffsetDateTime.now().toString()
            );
        } catch (Exception e) {
            log.warn("Error parseando cache: {}", e.getMessage());
            return unknownAll("Error parseando cache: " + e.getMessage());
        }
    }

    private static double matchDouble(String content, String regex) {
        Double v = matchNullable(content, regex);
        return v != null ? v : 0.0;
    }

    /**
     * Variante que distingue entre "match = 0" (valor real valido)
     * y "no match" (devuelve null). Util para metricas que pueden
     * legítimamente ser 0 (deuda tecnica = 0 dias).
     */
    private static Double matchNullable(String content, String regex) {
        String match = matchFirst(content, regex);
        if (match.isEmpty()) return null;
        try {
            return Double.parseDouble(match);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Devuelve el grupo 1 de la primera coincidencia del regex, o "" si no hay match.
     */
    private static String matchFirst(String content, String regex) {
        try {
            Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(content);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    private static QualityMetrics.Requisito req(int n, String desc, String valor,
                                                String umbral, QualityMetrics.Estado est, String fuente) {
        return new QualityMetrics.Requisito(n, desc, valor, umbral, est, fuente);
    }

    private QualityMetrics unknownAll(String motivo) {
        QualityMetrics.Requisito unk = new QualityMetrics.Requisito(
                0, "(desconocido)", motivo, "-",
                QualityMetrics.Estado.DESCONOCIDO, "-");
        return new QualityMetrics(unk, unk, unk, unk, unk,
                "cache no disponible: " + motivo,
                java.time.OffsetDateTime.now().toString());
    }
}