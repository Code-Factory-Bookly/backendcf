package com.bookly.backendcf.auth.infrastructure.persistence;

import com.bookly.backendcf.auth.domain.model.TrustedDevice;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, UUID> {

    List<TrustedDevice> findByUserIdAndExpiresAtAfter(UUID userId, OffsetDateTime now);

    void deleteByUserId(UUID userId);
}
