package com.bookly.backendcf.report.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.bookly.backendcf.report.domain.model.ReportPeriod;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pruebas del conversor del parámetro "periodo" (HU-15).
 *
 * <p>El conversor ignora mayúsculas y espacios; un texto vacío se trata como ausente y un valor
 * desconocido se rechaza con IllegalArgumentException.
 */
@Tag("unit")
class ReportPeriodConverterTest {

    private final ReportPeriodConverter converter = new ReportPeriodConverter();

    @ParameterizedTest
    @ValueSource(strings = {"MES", "mes", "Mes", "mEs"})
    void convierteElPeriodoMesIgnorandoLasMayusculas(String valor) {
        // Arrange
        String source = valor;

        // Act
        ReportPeriod period = converter.convert(source);

        // Assert
        assertThat(period).isEqualTo(ReportPeriod.MES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SEMANA", "semana", "Semana", "sEmAnA"})
    void convierteElPeriodoSemanaIgnorandoLasMayusculas(String valor) {
        // Arrange
        String source = valor;

        // Act
        ReportPeriod period = converter.convert(source);

        // Assert
        assertThat(period).isEqualTo(ReportPeriod.SEMANA);
    }

    @ParameterizedTest
    @ValueSource(strings = {" MES", "MES ", "  Mes  ", "\tmes\n"})
    void eliminaLosEspaciosAlRedeerDelValor(String valor) {
        // Arrange
        String source = valor;

        // Act
        ReportPeriod period = converter.convert(source);

        // Assert
        assertThat(period).isEqualTo(ReportPeriod.MES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void devuelveNullCuandoElValorEstaVacioParaQueElServicioUseElPeriodoPorDefecto(String valor) {
        // Arrange
        String source = valor;

        // Act
        ReportPeriod period = converter.convert(source);

        // Assert
        assertThat(period).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRIMESTRE", "SEMANA_MES", "1", "PENDIENTE", "MES DE ABRIL"})
    void rechazaUnPeriodoDesconocidoConIllegalArgumentException(String valor) {
        // Arrange
        String source = valor;

        // Act & Assert
        assertThatThrownBy(() -> converter.convert(source))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elMensajeDelErrorIndicaElValorInvalidoYLosPeriodosValidos() {
        // Arrange
        String source = "TRIMESTRE";

        // Act
        Throwable exception = catchThrowable(() -> converter.convert(source));

        // Assert
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains("TRIMESTRE");
    }
}
