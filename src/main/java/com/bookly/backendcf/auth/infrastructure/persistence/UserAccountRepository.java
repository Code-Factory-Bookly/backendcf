package com.bookly.backendcf.auth.infrastructure.persistence;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
}
