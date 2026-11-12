package com.bookly.backendcf.report.configuration;

import com.bookly.backendcf.report.domain.model.ReportPeriod;
import java.util.Locale;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Convierte el parámetro "periodo" ignorando mayúsculas y espacios: "mes", "Mes" y "MES" son el mismo valor.
 * Un texto vacío se trata como ausente (el servicio usa MES por defecto). Un valor desconocido lanza
 * IllegalArgumentException, que Spring convierte en MethodArgumentTypeMismatchException y el
 * GlobalExceptionHandler responde como 400 VALIDATION_ERROR.
 */
@Component
public class ReportPeriodConverter implements Converter<String, ReportPeriod> {

    @Override
    public ReportPeriod convert(String source) {
        String value = source.trim();
        if (value.isEmpty()) {
            return null;
        }
        return ReportPeriod.valueOf(value.toUpperCase(Locale.ROOT));
    }
}