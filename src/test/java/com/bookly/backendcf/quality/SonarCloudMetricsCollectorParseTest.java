package com.bookly.backendcf.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del parser JSON de SonarCloud (metodo parse).
 *
 * Estos tests ejercitan directamente la logica de parsing sin necesidad
 * de hacer HTTP real, llamando al metodo package-private parse() del
 * SonarCloudMetricsCollector. Aumentan significativamente la cobertura
 * del colector al ejercitar todas las ramas del JSON parser.
 */
class SonarCloudMetricsCollectorParseTest {

    private SonarCloudMetricsCollector collector = new SonarCloudMetricsCollector("");

    @Test
    @DisplayName("parse() con JSON completo y cobertura >= 65% produce PASS")
    void parseCoberturaAprobada() {
        String qg = "{\"projectStatus\":{\"alertStatus\":\"OK\"}}";
        String measures = "[{\"metric\":\"coverage\",\"value\":\"91.6\"}," +
                "\"metric\":\"line_coverage\",\"value\":\"93.9\"}," +
                "{\"metric\":\"branch_coverage\",\"value\":\"80.4\"}," +
                "{\"metric\":\"sqale_index\",\"value\":\"0\"}]";
        String issues = "{\"issues\":[]}";

        QualityMetrics m = collector.parse(qg, measures, issues);

        assertThat(m.req1Cobertura().estado()).isEqualTo(QualityMetrics.Estado.PASS);
        assertThat(m.req1Cobertura().valor()).contains("91.6");
        assertThat(m.req2DeudaTecnica().estado()).isEqualTo(QualityMetrics.Estado.PASS);
        assertThat(m.req4Severidad().estado()).isEqualTo(QualityMetrics.Estado.PASS);
        assertThat(m.req5Vulnerabilidades().estado()).isEqualTo(QualityMetrics.Estado.PASS);
    }

    @Test
    @DisplayName("parse() con cobertura < 65% produce FAIL")
    void parseCoberturaReprobada() {
        String qg = "{\"projectStatus\":{\"alertStatus\":\"OK\"}}";
        String measures = "[{\"metric\":\"coverage\",\"value\":\"50.0\"}]";
        String issues = "{\"issues\":[]}";

        QualityMetrics m = collector.parse(qg, measures, issues);

        assertThat(m.req1Cobertura().estado()).isEqualTo(QualityMetrics.Estado.FAIL);
    }

    @Test
    @DisplayName("parse() con deuda > 2 dias produce FAIL")
    void parseDeudaExcedida() {
        String qg = "{\"projectStatus\":{\"alertStatus\":\"OK\"}}";
        String measures = "[" +
                "{\"metric\":\"coverage\",\"value\":\"80\"}," +
                "{\"metric\":\"sqale_index\",\"value\":\"2880\"}" + // 2880 min = 6 dias
                "]";
        String issues = "{\"issues\":[]}";

        QualityMetrics m = collector.parse(qg, measures, issues);

        assertThat(m.req2DeudaTecnica().estado()).isEqualTo(QualityMetrics.Estado.FAIL);
        assertThat(m.req2DeudaTecnica().valor()).contains("dias");
        assertThat(m.req2DeudaTecnica().valor()).contains("2880");
    }

    @Test
    @DisplayName("parse() con Quality Gate en ERROR marca severidad como FAIL")
    void parseQgError() {
        String qg = "{\"projectStatus\":{\"alertStatus\":\"ERROR\"}}";
        String measures = "[]";
        String issues = "{\"issues\":[]}";

        QualityMetrics m = collector.parse(qg, measures, issues);

        assertThat(m.req4Severidad().estado()).isEqualTo(QualityMetrics.Estado.FAIL);
        assertThat(m.req4Severidad().valor()).contains("ERROR");
    }

    @Test
    @DisplayName("parse() con status OK marca severidad como PASS")
    void parseQgOk() {
        String qg = "{\"projectStatus\":{\"alertStatus\":\"OK\"}}";
        String measures = "[]";
        String issues = "{\"issues\":[]}";

        QualityMetrics m = collector.parse(qg, measures, issues);

        assertThat(m.req4Severidad().estado()).isEqualTo(QualityMetrics.Estado.PASS);
        assertThat(m.req4Severidad().valor()).contains("OK");
    }

    @Test
    @DisplayName("parse() cuenta vulnerabilidades CRITICAL correctamente")
    void parseCuentaCriticals() {
        String qg = "{\"projectStatus\":{\"alertStatus\":\"OK\"}}";
        String measures = "[]";
        String issues = "{\"issues\":[" +
                        "{\"severity\":\"CRITICAL\",\"message\":\"a\"}," +
                        "{\"severity\":\"CRITICAL\",\"message\":\"b\"}," +
                        "{\"severity\":\"MAJOR\",\"message\":\"c\"}" +
                        "]}";

        QualityMetrics m = collector.parse(qg, measures, issues);

        assertThat(m.req5Vulnerabilidades().valor()).contains("2");
        assertThat(m.req5Vulnerabilidades().estado()).isEqualTo(QualityMetrics.Estado.FAIL);
    }

    @Test
    @DisplayName("parse() sin CRITICAL marca vulns como PASS")
    void parseSinCriticals() {
        String qg = "{\"projectStatus\":{\"alertStatus\":\"OK\"}}";
        String measures = "[]";
        String issues = "{\"issues\":[" +
                        "{\"severity\":\"MAJOR\",\"message\":\"x\"}," +
                        "{\"severity\":\"MINOR\",\"message\":\"y\"}" +
                        "]}";

        QualityMetrics m = collector.parse(qg, measures, issues);

        assertThat(m.req5Vulnerabilidades().valor()).contains("0");
        assertThat(m.req5Vulnerabilidades().estado()).isEqualTo(QualityMetrics.Estado.PASS);
    }

    @Test
    @DisplayName("parse() maneja JSON invalido sin lanzar excepcion")
    void parseJsonInvalido() {
        QualityMetrics m = collector.parse("not-json", "{also bad", "}");

        // Todos los requisitos quedan en DESCONOCIDO por parseo fallido
        assertThat(m).isNotNull();
    }

    @Test
    @DisplayName("parse() maneja medidas vacias: req1 DESCONOCIDO, req2 PASS (0 dias)")
    void parseMedidasVacias() {
        String qg = "{\"projectStatus\":{\"alertStatus\":\"OK\"}}";
        QualityMetrics m = collector.parse(qg, "[]", "{\"issues\":[]}");

        // Sin metrica coverage: globalCov=0 → DESCONOCIDO
        assertThat(m.req1Cobertura().estado()).isEqualTo(QualityMetrics.Estado.DESCONOCIDO);
        // Sin metrica sqale_index: debtMin=0 → 0 dias → PASS (asumimos 0 deuda)
        assertThat(m.req2DeudaTecnica().estado()).isEqualTo(QualityMetrics.Estado.PASS);
        assertThat(m.req2DeudaTecnica().valor()).contains("0");
    }
}