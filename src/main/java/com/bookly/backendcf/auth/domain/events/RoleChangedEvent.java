package com.bookly.backendcf.auth.domain.events;

import com.bookly.backendcf.auth.domain.model.UserRole;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Event emitted after an effective role change has been persisted. */
public record RoleChangedEvent(
        UUID actorUserId,
        UUID targetUserId,
        UserRole previousRole,
        UserRole newRole,
        OffsetDateTime occurredAt) {
}
