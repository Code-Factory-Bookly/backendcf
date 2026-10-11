package com.bookly.backendcf.notification.infrastructure.persistence;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.catalog.domain.model.ServiceOffering;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.notification.application.BookingNotificationDataProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingNotificationDataProviderAdapter implements BookingNotificationDataProvider {

    private final UserAccountRepository userAccountRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;

    public BookingNotificationDataProviderAdapter(UserAccountRepository userAccountRepository,
                                                   ServiceOfferingRepository serviceOfferingRepository) {
        this.userAccountRepository = userAccountRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingNotificationData get(UUID customerId, UUID professionalId, UUID serviceId) {
        UserAccount customer = userAccountRepository.findById(customerId)
                .orElseThrow(() -> new IllegalStateException("Cliente de la reserva no encontrado"));
        UserAccount professional = userAccountRepository.findById(professionalId)
                .orElseThrow(() -> new IllegalStateException("Profesional de la reserva no encontrado"));
        ServiceOffering service = serviceOfferingRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalStateException("Servicio de la reserva no encontrado"));

        return new BookingNotificationData(
                customer.getEmail(),
                customer.getFullName(),
                professional.getFullName(),
                service.getName());
    }
}
