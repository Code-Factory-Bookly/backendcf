package com.bookly.backendcf.notification.infrastructure.persistence;

import com.bookly.backendcf.notification.domain.model.NotificationDelivery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    Optional<NotificationDelivery> findByEventId(UUID eventId);
}
