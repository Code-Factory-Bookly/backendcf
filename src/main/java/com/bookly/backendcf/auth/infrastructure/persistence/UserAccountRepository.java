package com.bookly.backendcf.auth.infrastructure.persistence;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    /**
     * Usado por el login de la HU-03.
     *
     * <p>OJO: desde que la unicidad del correo es {@code (tenant_id, email)}, el mismo correo puede
     * existir en varias organizaciones y esta consulta puede devolver más de un resultado. El login
     * necesita saber a qué organización se está entrando; queda por acordar con el equipo.
     */
    Optional<UserAccount> findByEmail(String email);
}
