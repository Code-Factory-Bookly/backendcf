package com.bookly.backendcf.notification.application;

import java.util.UUID;

public interface BookingNotificationDataProvider {

    BookingNotificationData get(UUID customerId, UUID professionalId, UUID serviceId);

    record BookingNotificationData(String customerEmail, String customerName,
                                   String professionalName, String serviceName) {
    }
}
