package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.presentation.dto.RoleAssignmentRequest;
import com.bookly.backendcf.auth.presentation.dto.RoleAssignmentResponse;
import com.bookly.backendcf.professional.domain.model.Professional;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleAssignmentService {

    private final UserAccountRepository userAccountRepository;
    private final ProfessionalRepository professionalRepository;

    public RoleAssignmentService(
            UserAccountRepository userAccountRepository,
            ProfessionalRepository professionalRepository) {
        this.userAccountRepository = userAccountRepository;
        this.professionalRepository = professionalRepository;
    }

    @Transactional
    public RoleAssignmentResponse assign(UUID userId, RoleAssignmentRequest request) {
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        boolean hasProfile = professionalRepository.existsById(userId);
        boolean becomesProfessional = UserRole.PROFESSIONAL.equals(request.role());
        if (request.role().equals(account.getRole())) {
            return RoleAssignmentResponse.from(account);
        }
        if (hasProfile && !becomesProfessional) {
            throw new ProfessionalProfileExistsException();
        }

        String specialty = request.specialty();
        if (becomesProfessional && !hasProfile && (specialty == null || specialty.isBlank())) {
            throw new SpecialtyRequiredException();
        }

        account.changeRole(request.role(), OffsetDateTime.now());
        UserAccount saved = userAccountRepository.saveAndFlush(account);

        if (becomesProfessional && !hasProfile) {
            professionalRepository.saveAndFlush(new Professional(userId, specialty.trim().replaceAll("\\s+", " ")));
        }
        return RoleAssignmentResponse.from(saved);
    }
}
