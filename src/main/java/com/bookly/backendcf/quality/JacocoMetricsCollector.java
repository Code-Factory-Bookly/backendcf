package com.bookly.backendcf.quality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Colector que lee el reporte CSV de JaCoCo generado en
 * `target/site/jacoco/jacoco.csv` despues de correr los tests.
 *
 * Si el reporte no existe (por ejemplo, si el backend se levanto
 * sin haber corrido `mvn test` antes), devuelve metricas con
 * estado DESCONOCIDO.
 */
@Component
public class JacocoMetricsCollector implements QualityMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(JacocoMetricsCollector.class);

    private static final Path CSV = Path.of("target/site/jacoco/jacoco.csv");

    @Override
    public String fuente() {
        return Files.exists(CSV) ? "JaCoCo local (target/site/jacoco)" : "JaCoCo local (reporte no generado)";
    }

    @Override
    public QualityMetrics collect() {
        if (!Files.exists(CSV)) {
            log.info("Jacoco CSV no encontrado en {}; corre 'mvn test' antes de arrancar el backend para tener cobertura", CSV);
            return desconocido("Reporte JaCoCo no encontrado. Ejecuta 'mvn test' antes de arrancar.");
        }

        try {
            String csv = Files.readString(CSV);
            // CSV header: GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,...
            // Solo nos interesan las filas con GROUP="" (clases del proyecto)
            long missedInstr = 0, coveredInstr = 0;
            long missedBranch = 0, coveredBranch = 0;
            long missedLine = 0, coveredLine = 0;
            double maxComplexity = 0;

            for (String line : csv.split("\n")) {
                if (line.isBlank() || line.startsWith("GROUP")) continue;
                String[] parts = line.split(",");
                if (parts.length < 12) continue;
                if (!parts[0].isEmpty()) continue; // solo GROUP=""
                try {
                    missedInstr += Long.parseLong(parts[3].trim());
                    coveredInstr += Long.parseLong(parts[4].trim());
                    missedBranch += Long.parseLong(parts[5].trim());
                    coveredBranch += Long.parseLong(parts[6].trim());
                    missedLine += Long.parseLong(parts[7].trim());
                    coveredLine += Long.parseLong(parts[8].trim());
                    double cm = Double.parseDouble(parts[9].trim()) + Double.parseDouble(parts[10].trim());
                    if (cm > maxComplexity) maxComplexity = cm;
                } catch (NumberFormatException ignore) {
                }
            }

            double lineCov = totalPct(coveredLine, missedLine);
            double branchCov = totalPct(coveredBranch, missedBranch);
            double instrCov = totalPct(coveredInstr, missedInstr);

            // #1 cobertura
            QualityMetrics.Estado e1 = instrCov >= 65.0
                    ? QualityMetrics.Estado.PASS
                    : QualityMetrics.Estado.FAIL;
            QualityMetrics.Requisito r1 = new QualityMetrics.Requisito(
                    1, "Cobertura de pruebas unitarias >= 65%",
                    String.format("%.1f%% (lineas %.1f%%, branches %.1f%%)", instrCov, lineCov, branchCov),
                    ">= 65%",
                    e1,
                    "JaCoCo local"
            );

            // #3 complejidad
            QualityMetrics.Estado e3 = maxComplexity < 50.0
                    ? QualityMetrics.Estado.PASS
                    : QualityMetrics.Estado.FAIL;
            QualityMetrics.Requisito r3 = new QualityMetrics.Requisito(
                    3, "Complejidad ciclomatica < 50 (por clase)",
                    String.format("max %.0f", maxComplexity),
                    "< 50",
                    e3,
                    "JaCoCo local"
            );

            // Los requisitos #2, #4, #5 no se miden en Jacoco
            QualityMetrics.Requisito r2 = new QualityMetrics.Requisito(
                    2, "Deuda tecnica <= 2 dias",
                    "(no medible en JaCoCo)", "<= 2 dias",
                    QualityMetrics.Estado.DESCONOCIDO, "JaCoCo no aplica");
            QualityMetrics.Requisito r4 = new QualityMetrics.Requisito(
                    4, "Severidad hallazgos aceptable",
                    "(no medible en JaCoCo)", "OK / sin blockers",
                    QualityMetrics.Estado.DESCONOCIDO, "JaCoCo no aplica");
            QualityMetrics.Requisito r5 = new QualityMetrics.Requisito(
                    5, "Cero vulnerabilidades criticas",
                    "(no medible en JaCoCo)", "= 0",
                    QualityMetrics.Estado.DESCONOCIDO, "JaCoCo no aplica");

            return new QualityMetrics(r1, r2, r3, r4, r5,
                    "JaCoCo local",
                    java.time.OffsetDateTime.now().toString());
        } catch (Exception e) {
            log.warn("Error leyendo JaCoCo CSV: {}", e.getMessage());
            return desconocido("Error parseando JaCoCo: " + e.getMessage());
        }
    }

    private static double totalPct(long covered, long missed) {
        long total = covered + missed;
        return total == 0 ? 0.0 : 100.0 * covered / total;
    }

    private QualityMetrics desconocido(String motivo) {
        QualityMetrics.Requisito unk = new QualityMetrics.Requisito(
                0, "(desconocido)", motivo, "-",
                QualityMetrics.Estado.DESCONOCIDO, "-");
        return new QualityMetrics(unk, unk, unk, unk, unk,
                "JaCoCo no disponible: " + motivo,
                java.time.OffsetDateTime.now().toString());
    }
}