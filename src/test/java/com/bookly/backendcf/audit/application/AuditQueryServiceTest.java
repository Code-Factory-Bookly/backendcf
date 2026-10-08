package com.bookly.backendcf.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.audit.domain.model.AuditAction;
import com.bookly.backendcf.audit.domain.model.AuditActionType;
import com.bookly.backendcf.audit.domain.model.AuditLog;
import com.bookly.backendcf.audit.domain.model.AuditResourceType;
import com.bookly.backendcf.audit.presentation.dto.AuditLogPageResponse;
import com.bookly.backendcf.audit.presentation.dto.AuditLogResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class AuditQueryServiceTest {

    private AuditLogQuery auditLogQuery;
    private AuditQueryService service;

    @BeforeEach
    void setUp() {
        auditLogQuery = mock(AuditLogQuery.class);
        service = new AuditQueryService(auditLogQuery);
    }

    @Test
    void consultaLaBitacoraYMapeaLaRespuestaPaginada() {
        OffsetDateTime from = OffsetDateTime.parse("2026-10-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-10-08T23:59:59Z");
        AuditLog auditLog = AuditLog.from(new AuditAction(
                UUID.randomUUID(),
                AuditActionType.BOOKING_CREATED,
                AuditResourceType.BOOKING,
                UUID.randomUUID(),
                to,
                "127.0.0.1",
                Map.of("status", "CONFIRMED")));
        when(auditLogQuery.findByOccurredAtBetween(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(auditLog), PageRequest.of(1, 2), 3));

        AuditLogPageResponse response = service.find(from, to, 1, 2);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
        AuditLogResponse item = response.content().get(0);
        assertThat(item.id()).isEqualTo(auditLog.getId());
        assertThat(item.actionType()).isEqualTo(AuditActionType.BOOKING_CREATED);
        assertThat(item.metadata()).containsEntry("status", "CONFIRMED");
        verify(auditLogQuery).findByOccurredAtBetween(any(), any(), any(Pageable.class));
    }

    @Test
    void rechazaUnRangoSinFechas() {
        assertThatThrownBy(() -> service.find(null, OffsetDateTime.now(), 0, 50))
                .isInstanceOf(InvalidAuditQueryException.class)
                .hasMessage("El rango de fechas es obligatorio")
                .satisfies(exception -> assertThat(((InvalidAuditQueryException) exception).getDetails())
                        .containsEntry("from", "Es obligatorio"));
        verifyNoInteractions(auditLogQuery);
    }

    @Test
    void rechazaUnaFechaInicialPosterior() {
        OffsetDateTime from = OffsetDateTime.parse("2026-10-09T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-10-08T00:00:00Z");

        assertThatThrownBy(() -> service.find(from, to, 0, 50))
                .isInstanceOf(InvalidAuditQueryException.class)
                .hasMessage("La fecha inicial no puede ser posterior a la fecha final");
    }

    @Test
    void rechazaUnaPaginaNegativa() {
        assertThatThrownBy(() -> service.find(OffsetDateTime.now(), OffsetDateTime.now(), -1, 50))
                .isInstanceOf(InvalidAuditQueryException.class)
                .hasMessage("La página no puede ser negativa");
    }

    @Test
    void rechazaUnTamañoDePaginaFueraDelRango() {
        OffsetDateTime now = OffsetDateTime.now();

        assertThatThrownBy(() -> service.find(now, now, 0, 101))
                .isInstanceOf(InvalidAuditQueryException.class)
                .hasMessage("El tamaño de página no es válido");
    }
}
