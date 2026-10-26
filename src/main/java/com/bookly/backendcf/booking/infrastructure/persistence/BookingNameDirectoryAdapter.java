package com.bookly.backendcf.booking.infrastructure.persistence;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.booking.application.BookingNameDirectory;
import com.bookly.backendcf.catalog.domain.model.ServiceOffering;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingNameDirectoryAdapter implements BookingNameDirectory {

    private final UserAccountRepository userAccountRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;

    public BookingNameDirectoryAdapter(
            UserAccountRepository userAccountRepository,
            ServiceOfferingRepository serviceOfferingRepository) {
        this.userAccountRepository = userAccountRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> accountNames(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return userAccountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getFullName));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> serviceNames(Collection<UUID> serviceIds) {
        if (serviceIds.isEmpty()) {
            return Map.of();
        }
        return serviceOfferingRepository.findAllById(serviceIds).stream()
                .collect(Collectors.toMap(ServiceOffering::getId, ServiceOffering::getName));
    }
}
