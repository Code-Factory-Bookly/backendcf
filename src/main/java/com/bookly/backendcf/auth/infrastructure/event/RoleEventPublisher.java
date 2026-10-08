package com.bookly.backendcf.auth.infrastructure.event;

import com.bookly.backendcf.auth.domain.events.RoleChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes authentication-domain events synchronously inside the current transaction. */
@Component
public class RoleEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public RoleEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(RoleChangedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
