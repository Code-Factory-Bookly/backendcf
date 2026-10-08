package com.bookly.backendcf.auth.infrastructure.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.bookly.backendcf.auth.domain.events.RoleChangedEvent;
import com.bookly.backendcf.auth.domain.model.UserRole;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class RoleEventPublisherTest {

    @Test
    void publicaElEventoDeCambioDeRolEnElContextoDeSpring() {
        ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
        RoleEventPublisher roleEventPublisher = new RoleEventPublisher(applicationEventPublisher);
        RoleChangedEvent event = new RoleChangedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UserRole.CUSTOMER,
                UserRole.PROFESSIONAL,
                OffsetDateTime.parse("2026-10-08T12:00:00Z"));

        roleEventPublisher.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }
}
