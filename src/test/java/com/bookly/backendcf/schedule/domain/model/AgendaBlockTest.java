package com.bookly.backendcf.schedule.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * HU-05: la entidad de bloqueo de agenda.
 *
 * <p>Referencia a los criterios de aceptacion (Gherkin, HU-05 refinada Sprint 2):
 * <ul>
 *   <li>Escenario "Bloquear una franja sin reservas previas".</li>
 * </ul>
 */
@Tag("unit")
class AgendaBlockTest {

    private final UUID professionalId = UUID.randomUUID();

    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // El constructor guarda profesional, rango y motivo tal como se pasan.
    @Test
    void guardaElProfesionalElRangoYElMotivo() {
        // Arrange
        LocalDateTime inicio = LocalDateTime.now().plusDays(30).withHour(8).withMinute(0);
        LocalDateTime fin = inicio.plusHours(4);

        // Act
        AgendaBlock block = new AgendaBlock(professionalId, inicio, fin, "Vacaciones");

        // Assert
        assertThat(block.getProfessionalId()).isEqualTo(professionalId);
        assertThat(block.getStartTime()).isEqualTo(inicio);
        assertThat(block.getEndTime()).isEqualTo(fin);
        assertThat(block.getReason()).isEqualTo("Vacaciones");
    }

    // QA/Exception: el motivo es opcional y puede quedar en null.
    @Test
    void permiteElMotivoNulo() {
        // Arrange
        LocalDateTime inicio = LocalDateTime.now().plusDays(30).withHour(8).withMinute(0);
        LocalDateTime fin = inicio.plusHours(4);

        // Act
        AgendaBlock block = new AgendaBlock(professionalId, inicio, fin, null);

        // Assert
        assertThat(block.getReason()).isNull();
    }

    // QA/Exception: la fecha de creacion se genera al construir el bloqueo.
    @Test
    void generaLaFechaDeCreacionAlConstruirElBloqueo() {
        // Arrange
        LocalDateTime inicio = LocalDateTime.now().plusDays(30).withHour(8).withMinute(0);
        LocalDateTime fin = inicio.plusHours(4);
        OffsetDateTime antes = OffsetDateTime.now();

        // Act
        AgendaBlock block = new AgendaBlock(professionalId, inicio, fin, "Imprevisto");

        // Assert
        assertThat(block.getCreatedAt()).isNotNull().isBetween(antes, OffsetDateTime.now());
    }

    // QA/Exception: el id lo asigna la base de datos, no el constructor.
    @Test
    void elIdLoAsignaLaBaseDeDatos() {
        // Arrange
        LocalDateTime inicio = LocalDateTime.now().plusDays(30).withHour(8).withMinute(0);
        LocalDateTime fin = inicio.plusHours(4);
        UUID idEsperado = UUID.randomUUID();

        // Act
        AgendaBlock block = new AgendaBlock(professionalId, inicio, fin, null);
        ReflectionTestUtils.setField(block, "id", idEsperado);

        // Assert
        assertThat(block.getId()).isEqualTo(idEsperado);
    }
}
