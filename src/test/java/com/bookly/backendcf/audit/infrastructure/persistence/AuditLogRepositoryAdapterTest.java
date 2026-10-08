package com.bookly.backendcf.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.audit.domain.model.AuditLog;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class AuditLogRepositoryAdapterTest {

    @Test
    void delegaLaConsultaPorRangoAlRepositorio() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogRepositoryAdapter adapter = new AuditLogRepositoryAdapter(repository);
        OffsetDateTime from = OffsetDateTime.parse("2026-10-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-10-08T23:59:59Z");
        Page<AuditLog> expected = new PageImpl<>(java.util.List.of());
        when(repository.findByOccurredAtBetweenOrderByOccurredAtDescIdDesc(any(), any(), any()))
                .thenReturn(expected);

        Page<AuditLog> result = adapter.findByOccurredAtBetween(from, to, Pageable.unpaged());

        assertThat(result).isSameAs(expected);
        verify(repository).findByOccurredAtBetweenOrderByOccurredAtDescIdDesc(from, to, Pageable.unpaged());
    }

    @Test
    void delegaElGuardadoAlRepositorio() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogRepositoryAdapter adapter = new AuditLogRepositoryAdapter(repository);
        AuditLog auditLog = mock(AuditLog.class);
        when(repository.saveAndFlush(auditLog)).thenReturn(auditLog);

        assertThat(adapter.saveAndFlush(auditLog)).isSameAs(auditLog);
        verify(repository).saveAndFlush(auditLog);
    }
}
