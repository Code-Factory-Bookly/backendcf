package com.bookly.backendcf.notification.application;

import java.util.UUID;

public interface BookingNotificationDataProvider {

    BookingNotificationData get(UUID customerId, UUID professionalId, UUID serviceId);

    /**
     * {@code professionalEmail} se usa solo para avisar al profesional de una cancelacion
     * (HU-10 / HU-14); puede ser nulo y entonces no se le envia nada.
     */
    record BookingNotificationData(String customerEmail, String customerName,
                                   String professionalName, String serviceName,
                                   String professionalEmail) {

        public BookingNotificationData(String customerEmail, String customerName,
                                       String professionalName, String serviceName) {
            this(customerEmail, customerName, professionalName, serviceName, null);
        }
    }
}
