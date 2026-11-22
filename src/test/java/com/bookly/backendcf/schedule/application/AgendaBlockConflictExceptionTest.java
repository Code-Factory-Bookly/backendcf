package com.bookly.backendcf.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.shared.error.ResourceConflictException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * HU-05: la excepcion de conflicto del bloqueo de agenda.
 *
 * <p>Referencia a los criterios de aceptacion (Gherkin, HU-05 refinada Sprint 2):
 * <ul>
 *   <li>Escenario "Intento de bloquear rango con reservas confirmadas activas": el sistema rechaza
 *       el bloqueo y presenta las citas afectadas.</li>
 * </ul>
 *
 * <p>Reglas y errores: {@code docs/HU-05-bloqueo-agenda.md}.
 */
@Tag("unit")
class AgendaBlockConflictExceptionTest {

    // Escenario Gherkin HU-05: Intento de bloquear rango con reservas confirmadas activas.
    // El conflicto por reservas confirmadas usa su propio codigo de error.
    @Test
    void elConflictoPorReservasConfirmadasUsaSuPropioCodigoDeError() {
        // Arrange
        Map<String, String> citas = Map.of("reserva-1", "21/12 09:00–10:00 · Ana · Consulta");

        // Act
        AgendaBlockConflictException exception = AgendaBlockConflictException.bookingsInRange(citas);

        // Assert
        assertThat(exception.getErrorCode()).isEqualTo("AGENDA_BLOCK_HAS_BOOKINGS");
    }

    // QA/Exception: el conflicto por solapamiento usa su propio codigo de error.
    @Test
    void elConflictoPorSolapamientoUsaSuPropioCodigoDeError() {
        // Arrange
        Map<String, String> bloqueos = Map.of("bloqueo-1", "21/12 08:00–12:00");

        // Act
        AgendaBlockConflictException exception = AgendaBlockConflictException.overlapsBlock(bloqueos);

        // Assert
        assertThat(exception.getErrorCode()).isEqualTo("AGENDA_BLOCK_OVERLAP");
    }

    // Escenario Gherkin HU-05: Intento de bloquear rango con reservas confirmadas activas.
    // La excepcion expone el detalle recibido sin transformarlo.
    @Test
    void exponeElDetalleDeLasCitasAfectadas() {
        // Arrange
        Map<String, String> citas = new LinkedHashMap<>();
        citas.put("reserva-1", "21/12 09:00–10:00 · Ana · Consulta");
        citas.put("reserva-2", "21/12 11:00–12:00 · Luis · Terapia");

        // Act
        AgendaBlockConflictException exception = AgendaBlockConflictException.bookingsInRange(citas);

        // Assert
        assertThat(exception.getDetails())
                .containsOnlyKeys("reserva-1", "reserva-2")
                .containsEntry("reserva-1", "21/12 09:00–10:00 · Ana · Consulta");
    }

    // QA/Exception: el detalle del solapamiento se expone con las claves de los bloqueos.
    @Test
    void exponeElDetalleDeLosBloqueosSolapados() {
        // Arrange
        Map<String, String> bloqueos = Map.of("bloqueo-1", "21/12 08:00–12:00");

        // Act
        AgendaBlockConflictException exception = AgendaBlockConflictException.overlapsBlock(bloqueos);

        // Assert
        assertThat(exception.getDetails())
                .containsOnlyKeys("bloqueo-1")
                .containsEntry("bloqueo-1", "21/12 08:00–12:00");
    }

    // QA/Exception: ambas situaciones son conflictos de recurso, asi que el manejador global
    // responde 409.
    @Test
    void esUnConflictoDeRecursoParaQueElManejadorGlobalResponda409() {
        // Arrange
        Map<String, String> detalle = Map.of("clave", "valor");

        // Act
        AgendaBlockConflictException conReservas = AgendaBlockConflictException.bookingsInRange(detalle);
        AgendaBlockConflictException conSolapamiento = AgendaBlockConflictException.overlapsBlock(detalle);

        // Assert
        assertThat(conReservas).isInstanceOf(ResourceConflictException.class);
        assertThat(conSolapamiento).isInstanceOf(ResourceConflictException.class);
    }

    // Escenario Gherkin HU-05: Intento de bloquear rango con reservas confirmadas activas.
    // El mensaje explica que hay que cancelar o reprogramar las citas.
    @Test
    void elMensajeExplicaQueHayQueCancelarOReprogramar() {
        // Arrange
        Map<String, String> detalle = Map.of("clave", "valor");

        // Act
        AgendaBlockConflictException exception = AgendaBlockConflictException.bookingsInRange(detalle);

        // Assert
        assertThat(exception.getMessage())
                .contains("reservas confirmadas")
                .contains("Cancélalas")
                .contains("reprográmalas");
    }

    // QA/Exception: el mensaje del solapamiento menciona que ya existe un bloqueo.
    @Test
    void elMensajeDelSolapamientoMencionaQueYaExisteUnBloqueo() {
        // Arrange
        Map<String, String> detalle = Map.of("clave", "valor");

        // Act
        AgendaBlockConflictException exception = AgendaBlockConflictException.overlapsBlock(detalle);

        // Assert
        assertThat(exception.getMessage()).contains("Ya existe un bloqueo");
    }
}
