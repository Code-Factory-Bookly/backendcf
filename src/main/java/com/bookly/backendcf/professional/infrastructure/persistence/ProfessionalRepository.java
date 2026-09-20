package com.bookly.backendcf.professional.infrastructure.persistence;

import com.bookly.backendcf.professional.domain.model.Professional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessionalRepository extends JpaRepository<Professional, UUID> {
}