package com.bookly.backendcf.quality;

/**
 * Snapshot inmutable de las cinco metricas de calidad exigidas por el
 * Sprint 2 / Sprint 3.
 *
 * Cada requisito incluye:
 *   - numero (#1..#5)
 *   - descripcion legible
 *   - valor actual (formateado)
 *   - umbral exigido
 *   - estado: PASS / WARN / FAIL
 *   - fuente: de donde se obtuvo el dato
 */
public record QualityMetrics(
        Requisito req1Cobertura,
        Requisito req2DeudaTecnica,
        Requisito req3Complejidad,
        Requisito req4Severidad,
        Requisito req5Vulnerabilidades,
        String fuentePrincipal,
        String timestamp
) {

    /**
     * Un requisito individual de calidad.
     */
    public record Requisito(
            int numero,
            String descripcion,
            String valor,
            String umbral,
            Estado estado,
            String fuente
    ) {}

    public enum Estado {
        PASS, WARN, FAIL, DESCONOCIDO;

        public String simbolo() {
            return switch (this) {
                case PASS -> "[OK]";
                case WARN -> "[!!]";
                case FAIL -> "[XX]";
                case DESCONOCIDO -> "[?]";
            };
        }
    }

    /**
     * Determina si TODOS los 5 requisitos pasan.
     */
    public boolean todosPasan() {
        return req1Cobertura.estado() == Estado.PASS
                && req2DeudaTecnica.estado() == Estado.PASS
                && req3Complejidad.estado() == Estado.PASS
                && req4Severidad.estado() == Estado.PASS
                && req5Vulnerabilidades.estado() == Estado.PASS;
    }

    public long cantidadPass() {
        return java.util.stream.Stream.of(
                req1Cobertura, req2DeudaTecnica, req3Complejidad,
                req4Severidad, req5Vulnerabilidades)
                .filter(r -> r.estado() == Estado.PASS)
                .count();
    }

    public long cantidadFail() {
        return java.util.stream.Stream.of(
                req1Cobertura, req2DeudaTecnica, req3Complejidad,
                req4Severidad, req5Vulnerabilidades)
                .filter(r -> r.estado() == Estado.FAIL)
                .count();
    }

    /**
     * Factory: retorna un QualityMetrics con todos los requisitos
     * en estado DESCONOCIDO. Util para fallbacks seguros.
     */
    public static QualityMetrics unknownAll() {
        Requisito unk = new Requisito(
                0, "(sin dato)", "(sin dato)", "-",
                Estado.DESCONOCIDO, "ninguna fuente disponible");
        return new QualityMetrics(unk, unk, unk, unk, unk,
                "ninguna fuente disponible",
                java.time.OffsetDateTime.now().toString());
    }
}