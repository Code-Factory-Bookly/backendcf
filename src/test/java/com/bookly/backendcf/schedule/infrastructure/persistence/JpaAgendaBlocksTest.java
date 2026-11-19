package com.bookly.backendcf.schedule.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.schedule.application.AgendaBlocks;
import com.bookly.backendcf.schedule.domain.model.AgendaBlock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * HU-05: el adaptador JPA que expone los bloqueos de agenda a los demas modulos.
 *
 * <p>Referencia a los criterios de aceptacion (Gherkin, HU-05 refinada Sprint 2):
 * <ul>
 *   <li>Escenario "Bloquear una franja sin reservas previas": el sistema deja de ofrecer esas
 *       franjas a los clientes de forma inmediata.</li>
 * </ul>
 */
@Tag("unit")
class JpaAgendaBlocksTest {

    private final UUID professionalId = UUID.randomUUID();

    /**
     * Rango relativo al momento de ejecucion: una fecha fija acabaria expirando la suite.
     */
    private LocalDateTime from;
    private LocalDateTime to;

    private AgendaBlockRepository repository;
    private JpaAgendaBlocks adapter;

    @BeforeEach
    void setUp() {
        from = LocalDateTime.now().plusDays(30).withHour(8).withMinute(0).withSecond(0).withNano(0);
        to = from.plusHours(10);
        repository = mock(AgendaBlockRepository.class);
        adapter = new JpaAgendaBlocks(repository);
    }

    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Delega la consulta de solapamientos al repositorio.
    @Test
    void delegaLaConsultaDeSolapamientosAlRepositorio() {
        // Arrange
        when(repository.findOverlapping(professionalId, from, to)).thenReturn(List.of());

        // Act
        List<AgendaBlocks.BlockedPeriod> periods = adapter.findOverlapping(professionalId, from, to);

        // Assert
        assertThat(periods).isEmpty();
        verify(repository).findOverlapping(professionalId, from, to);
    }

    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Cada bloqueo encontrado se traduce a un periodo con inicio y fin.
    @Test
    void traduceCadaBloqueoAPeriodoOcupado() {
        // Arrange
        LocalDateTime inicio = from.plusHours(1);
        LocalDateTime fin = from.plusHours(2);
        when(repository.findOverlapping(professionalId, from, to)).thenReturn(List.of(block(inicio, fin)));

        // Act
        List<AgendaBlocks.BlockedPeriod> periods = adapter.findOverlapping(professionalId, from, to);

        // Assert
        assertThat(periods).containsExactly(new AgendaBlocks.BlockedPeriod(inicio, fin));
    }

    // QA/Exception: el adaptador devuelve los periodos en el mismo orden que el repositorio.
    @Test
    void conservaElOrdenDeLosPeriodosDevueltosPorElRepositorio() {
        // Arrange
        LocalDateTime inicio = from.plusHours(1);
        LocalDateTime fin = from.plusHours(2);
        LocalDateTime inicioTarde = from.plusHours(6);
        LocalDateTime finTarde = from.plusHours(7);
        when(repository.findOverlapping(professionalId, from, to)).thenReturn(List.of(
                block(inicio, fin),
                block(inicioTarde, finTarde)));

        // Act
        List<AgendaBlocks.BlockedPeriod> periods = adapter.findOverlapping(professionalId, from, to);

        // Assert
        assertThat(periods).containsExactly(
                new AgendaBlocks.BlockedPeriod(inicio, fin),
                new AgendaBlocks.BlockedPeriod(inicioTarde, finTarde));
    }

    // QA/Exception: el periodo solo expone inicio y fin, no el motivo ni el id.
    @Test
    void elPeriodoExponeSoloElInicioYElFin() {
        // Arrange
        LocalDateTime inicio = from.plusHours(1);
        LocalDateTime fin = from.plusHours(2);
        when(repository.findOverlapping(professionalId, from, to)).thenReturn(List.of(block(inicio, fin)));

        // Act
        AgendaBlocks.BlockedPeriod period =
                adapter.findOverlapping(professionalId, from, to).get(0);

        // Assert
        assertThat(period.startTime()).isEqualTo(inicio);
        assertThat(period.endTime()).isEqualTo(fin);
    }

    // QA/Exception: el puerto NONE sirve a los constructores que no reciben bloqueos.
    @Test
    void elPuertoNoneNoDevuelveNingunBloqueo() {
        // Act
        List<AgendaBlocks.BlockedPeriod> periods = AgendaBlocks.NONE.findOverlapping(professionalId, from, to);

        // Assert
        assertThat(periods).isEmpty();
    }

    private AgendaBlock block(LocalDateTime start, LocalDateTime end) {
        AgendaBlock block = new AgendaBlock(professionalId, start, end, "Vacaciones");
        ReflectionTestUtils.setField(block, "id", UUID.randomUUID());
        return block;
    }
}
