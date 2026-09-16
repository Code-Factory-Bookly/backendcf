package com.bookly.backendcf.platform.infrastructure.persistence;

import com.bookly.backendcf.platform.domain.model.Platform;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformRepository extends JpaRepository<Platform, UUID> {
}
