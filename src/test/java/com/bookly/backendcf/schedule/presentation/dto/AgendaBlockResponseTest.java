package com.bookly.backendcf.schedule.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.schedule.domain.model.AgendaBlock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * HU-05: el DTO de respuesta del bloqueo.
 *
 * <p>Referencia a los criterios de aceptacion (Gherkin, HU-05 refinada Sprint 2):
 * <ul>
 *   <li>Escenario "Bloquear una franja sin reservas previas".</li>
 * </ul>
 */
@Tag("unit")
class AgendaBlockResponseTest {

    private final UUID professionalId = UUID.randomUUID();

    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // La respuesta replica los datos del bloqueo.
    @Test
    void replicaLosDatosDelBloqueo() {
        // Arrange
        LocalDateTime inicio = LocalDateTime.now().plusDays(30).withHour(8).withMinute(0);
        LocalDateTime fin = inicio.plusHours(4);
        UUID blockId = UUID.randomUUID();
        AgendaBlock block = new AgendaBlock(professionalId, inicio, fin, "Vacaciones");
        ReflectionTestUtils.setField(block, "id", blockId);

        // Act
        AgendaBlockResponse response = AgendaBlockResponse.from(block);

        // Assert
        assertThat(response.id()).isEqualTo(blockId);
        assertThat(response.professionalId()).isEqualTo(professionalId);
        assertThat(response.startTime()).isEqualTo(inicio);
        assertThat(response.endTime()).isEqualTo(fin);
        assertThat(response.reason()).isEqualTo("Vacaciones");
        assertThat(response.createdAt()).isEqualTo(block.getCreatedAt());
    }

    // QA/Exception: un bloqueo sin motivo se responde con motivo nulo.
    @Test
    void respondeConMotivoNuloCuandoElBloqueoNoTieneMotivo() {
        // Arrange
        LocalDateTime inicio = LocalDateTime.now().plusDays(30).withHour(8).withMinute(0);
        LocalDateTime fin = inicio.plusHours(4);
        AgendaBlock block = new AgendaBlock(professionalId, inicio, fin, null);
        ReflectionTestUtils.setField(block, "id", UUID.randomUUID());

        // Act
        AgendaBlockResponse response = AgendaBlockResponse.from(block);

        // Assert
        assertThat(response.reason()).isNull();
    }

    // QA/Exception: la respuesta conserva el instante de creacion del bloqueo.
    @Test
    void conservaElInstanteDeCreacionDelBloqueo() {
        // Arrange
        LocalDateTime inicio = LocalDateTime.now().plusDays(30).withHour(8).withMinute(0);
        LocalDateTime fin = inicio.plusHours(4);
        AgendaBlock block = new AgendaBlock(professionalId, inicio, fin, "Imprevisto");
        ReflectionTestUtils.setField(block, "id", UUID.randomUUID());
        OffsetDateTime creado = block.getCreatedAt();

        // Act
        AgendaBlockResponse response = AgendaBlockResponse.from(block);

        // Assert
        assertThat(response.createdAt()).isEqualTo(creado);
    }
}
