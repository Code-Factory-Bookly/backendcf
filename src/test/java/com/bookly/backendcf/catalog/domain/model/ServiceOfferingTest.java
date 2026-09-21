package com.bookly.backendcf.catalog.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ServiceOffering - validación de duración")
class ServiceOfferingTest {

    @ParameterizedTest(name = "duración válida: {0} minutos")
    @ValueSource(ints = {1, 30, 45, 60, 120, 480})
    void shouldAcceptValidDuration(int minutes) {
        ServiceOffering offering = new ServiceOffering(
                "Corte de cabello", "Descripción", "Peluquería", minutes, BigDecimal.valueOf(25000));

        assertEquals(minutes, offering.getDurationMinutes());
        assertEquals(ServiceStatus.ACTIVO, offering.getStatus());
        assertNotNull(offering.getCreatedAt());
    }

    @Test
    @DisplayName("duración estándar de 45 minutos - escenario HU-06")
    void shouldAccept45MinutesDuration() {
        ServiceOffering offering = new ServiceOffering(
                "Limpieza dental", "Limpieza profesional", "Odontología", 45, BigDecimal.valueOf(80000));

        assertEquals(45, offering.getDurationMinutes());
    }

    @Test
    @DisplayName("actualizar duración de un servicio existente")
    void shouldUpdateDuration() {
        ServiceOffering offering = new ServiceOffering(
                "Manicure", "Desc", "Belleza", 30, BigDecimal.valueOf(20000));

        offering.update("Manicure", "Desc", "Belleza", 60, BigDecimal.valueOf(20000), null);

        assertEquals(60, offering.getDurationMinutes());
        assertNotNull(offering.getUpdatedAt());
    }

    @Test
    @DisplayName("actualizar duración al máximo permitido (480)")
    void shouldAcceptMaxDuration() {
        ServiceOffering offering = new ServiceOffering(
                "Spa completo", null, "Spa", 480, BigDecimal.valueOf(500000));

        assertEquals(480, offering.getDurationMinutes());
    }

    @Test
    @DisplayName("el estado nace ACTIVO por defecto")
    void shouldDefaultToActivo() {
        ServiceOffering offering = new ServiceOffering(
                "Servicio", null, "Cat", 30, BigDecimal.valueOf(10000));

        assertEquals(ServiceStatus.ACTIVO, offering.getStatus());
    }
}