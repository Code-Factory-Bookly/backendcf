package com.bookly.backendcf.audit.infrastructure.persistence;

import com.bookly.backendcf.audit.application.AuditActorDirectory;
import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserAccountAuditActorDirectory implements AuditActorDirectory {

    private final UserAccountRepository userAccountRepository;

    public UserAccountAuditActorDirectory(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Actor> findByIds(Collection<UUID> actorIds) {
        if (actorIds.isEmpty()) {
            return Map.of();
        }
        return userAccountRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(
                        UserAccount::getId,
                        account -> new Actor(account.getFullName(), account.getEmail())));
    }
}
