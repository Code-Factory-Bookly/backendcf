package com.bookly.backendcf.booking.infrastructure.event;

import com.bookly.backendcf.booking.domain.events.BookingEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class BookingEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public BookingEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(BookingEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}