package com.bookly.backendcf.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.audit.domain.model.AuditAction;
import com.bookly.backendcf.audit.domain.model.AuditActionType;
import com.bookly.backendcf.audit.domain.model.AuditLog;
import com.bookly.backendcf.audit.domain.model.AuditResourceType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@SpringBootTest
class AuditLogPersistenceTest {

    @Autowired
    private AuditLogRepository repository;

    @Test
    void persisteYConsultaLaEntradaSinPerderLaMetadata() {
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-10-07T10:00:00Z");
        AuditLog saved = repository.saveAndFlush(action(occurredAt, Map.of("status", "CONFIRMED")));

        AuditLog found = repository.findByOccurredAtBetweenOrderByOccurredAtDescIdDesc(
                        occurredAt.minusMinutes(1), occurredAt.plusMinutes(1), PageRequest.of(0, 10))
                .getContent()
                .get(0);

        assertThat(saved.getId()).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getActorUserId()).isEqualTo(saved.getActorUserId());
        assertThat(found.getActionType()).isEqualTo(AuditActionType.BOOKING_CREATED);
        assertThat(found.getResourceType()).isEqualTo(AuditResourceType.BOOKING);
        assertThat(found.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(found.getMetadata()).containsEntry("status", "CONFIRMED");
    }

    @Test
    void consultaPorFechaMantieneOrdenDescendenteDeterminista() {
        OffsetDateTime older = OffsetDateTime.parse("2026-10-08T09:00:00Z");
        OffsetDateTime newer = OffsetDateTime.parse("2026-10-08T11:00:00Z");
        repository.saveAndFlush(action(older, Map.of("order", "older")));
        repository.saveAndFlush(action(newer, Map.of("order", "newer")));

        Page<AuditLog> page = repository.findByOccurredAtBetweenOrderByOccurredAtDescIdDesc(
                older.minusMinutes(1), newer.plusMinutes(1), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).extracting(AuditLog::getOccurredAt)
                .containsExactly(newer, older);
    }

    @Test
    void repositorioNoDeclaraOperacionesDeActualizacionNiEliminacion() {
        assertThat(AuditLogRepository.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder(
                        "saveAndFlush",
                        "findByOccurredAtBetweenOrderByOccurredAtDescIdDesc");
    }

    private AuditLog action(OffsetDateTime occurredAt, Map<String, String> metadata) {
        return AuditLog.from(new AuditAction(
                UUID.randomUUID(),
                AuditActionType.BOOKING_CREATED,
                AuditResourceType.BOOKING,
                UUID.randomUUID(),
                occurredAt,
                "127.0.0.1",
                metadata));
    }
}
