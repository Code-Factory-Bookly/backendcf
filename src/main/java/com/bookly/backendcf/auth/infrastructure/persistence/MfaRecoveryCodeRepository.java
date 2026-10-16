package com.bookly.backendcf.auth.infrastructure.persistence;

import com.bookly.backendcf.auth.domain.model.MfaRecoveryCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    List<MfaRecoveryCode> findByUserIdAndUsedAtIsNull(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByUserId(UUID userId);
}
