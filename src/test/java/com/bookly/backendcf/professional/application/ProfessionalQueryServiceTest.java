package com.bookly.backendcf.professional.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.professional.domain.model.Professional;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfessionalQueryServiceTest {

    @Test
    void listaProfesionalesConDatosDeSuCuentaOrdenadosPorNombre() throws Exception {
        UUID zoeId = UUID.randomUUID();
        UUID annaId = UUID.randomUUID();
        ProfessionalRepository professionalRepository = mock(ProfessionalRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(professionalRepository.findAll()).thenReturn(List.of(
                new Professional(zoeId, "Ortodoncia"),
                new Professional(annaId, "Limpieza")));
        when(userAccountRepository.findAllById(anyList())).thenReturn(List.of(
                account(zoeId, "zoe@bookly.co", "Zoe Ruiz"),
                account(annaId, "anna@bookly.co", "Anna Lopez")));

        var result = new ProfessionalQueryService(professionalRepository, userAccountRepository).list();

        assertThat(result).extracting(response -> response.fullName()).containsExactly("Anna Lopez", "Zoe Ruiz");
        assertThat(result.get(0).role()).isEqualTo(UserRole.PROFESSIONAL);
        assertThat(result.get(0).specialty()).isEqualTo("Limpieza");
    }

    private UserAccount account(UUID id, String email, String fullName) throws Exception {
        UserAccount account = new UserAccount(email, "hash", fullName, UserRole.PROFESSIONAL);
        Field idField = UserAccount.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(account, id);
        return account;
    }
}
