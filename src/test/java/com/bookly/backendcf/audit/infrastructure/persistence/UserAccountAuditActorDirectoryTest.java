package com.bookly.backendcf.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.audit.application.AuditActorDirectory;
import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserAccountAuditActorDirectoryTest {

    @Autowired
    private UserAccountAuditActorDirectory directory;

    @Autowired
    private UserAccountRepository accountRepository;

    @Test
    void resuelveNombreYCorreoDeLasCuentasExistentes() {
        UserAccount account = accountRepository.save(new UserAccount(
                "actor-" + UUID.randomUUID() + "@example.com", "hash", "Actor Auditado", UserRole.ADMIN));
        UUID missing = UUID.randomUUID();

        Map<UUID, AuditActorDirectory.Actor> actors = directory.findByIds(List.of(account.getId(), missing));

        assertThat(actors).containsOnlyKeys(account.getId());
        assertThat(actors.get(account.getId()).fullName()).isEqualTo("Actor Auditado");
        assertThat(actors.get(account.getId()).email()).isEqualTo(account.getEmail());
    }

    @Test
    void sinIdsNoConsultaNada() {
        assertThat(directory.findByIds(List.of())).isEmpty();
    }
}
