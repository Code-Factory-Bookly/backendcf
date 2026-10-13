package com.bookly.backendcf.professional.infrastructure.persistence;

import com.bookly.backendcf.professional.domain.model.ProfessionalServiceAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessionalServiceRepository extends JpaRepository<ProfessionalServiceAssignment, UUID> {

    List<ProfessionalServiceAssignment> findByServiceId(UUID serviceId);

    List<ProfessionalServiceAssignment> findByProfessionalId(UUID professionalId);

    void deleteByProfessionalId(UUID professionalId);
}
