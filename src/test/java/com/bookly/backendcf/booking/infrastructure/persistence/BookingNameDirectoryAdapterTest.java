package com.bookly.backendcf.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.catalog.domain.model.ServiceOffering;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BookingNameDirectoryAdapterTest {

    @Autowired
    private BookingNameDirectoryAdapter directory;

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private ServiceOfferingRepository serviceRepository;

    @Test
    void resuelveNombresDeCuentasExistentes() {
        UserAccount account = accountRepository.save(new UserAccount(
                "nombre-" + UUID.randomUUID() + "@example.com", "hash", "Laura Gomez", UserRole.CUSTOMER));

        var names = directory.accountNames(List.of(account.getId(), UUID.randomUUID()));

        assertThat(names).containsOnlyKeys(account.getId());
        assertThat(names.get(account.getId())).isEqualTo("Laura Gomez");
    }

    @Test
    void resuelveNombresDeServiciosExistentes() {
        ServiceOffering service = serviceRepository.save(new ServiceOffering(
                "Corte clasico " + UUID.randomUUID(), "desc", "Barberia", 45, BigDecimal.TEN));

        var names = directory.serviceNames(List.of(service.getId(), UUID.randomUUID()));

        assertThat(names).containsOnlyKeys(service.getId());
        assertThat(names.get(service.getId())).isEqualTo(service.getName());
    }

    @Test
    void sinIdsNoConsultaNada() {
        assertThat(directory.accountNames(List.of())).isEmpty();
        assertThat(directory.serviceNames(List.of())).isEmpty();
    }
}
