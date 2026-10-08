package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.domain.events.RoleChangedEvent;
import com.bookly.backendcf.auth.infrastructure.event.RoleEventPublisher;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.presentation.dto.RoleAssignmentRequest;
import com.bookly.backendcf.auth.presentation.dto.RoleAssignmentResponse;
import com.bookly.backendcf.auth.presentation.dto.UserSearchResult;
import com.bookly.backendcf.professional.domain.model.Professional;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.shared.security.ActorIdentityProvider;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleAssignmentService {

    private final UserAccountRepository userAccountRepository;
    private final ProfessionalRepository professionalRepository;
    private final RoleEventPublisher roleEventPublisher;
    private final ActorIdentityProvider actorIdentityProvider;

    public RoleAssignmentService(
            UserAccountRepository userAccountRepository,
            ProfessionalRepository professionalRepository,
            RoleEventPublisher roleEventPublisher,
            ActorIdentityProvider actorIdentityProvider) {
        this.userAccountRepository = userAccountRepository;
        this.professionalRepository = professionalRepository;
        this.roleEventPublisher = roleEventPublisher;
        this.actorIdentityProvider = actorIdentityProvider;
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

        UserRole previousRole = account.getRole();
        UUID actorUserId = actorIdentityProvider.currentActorId();
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
        roleEventPublisher.publish(new RoleChangedEvent(
                actorUserId,
                userId,
                previousRole,
                saved.getRole(),
                OffsetDateTime.now()));
        return RoleAssignmentResponse.from(saved);
    }

    // Busqueda por correo o nombre para la asignacion de roles: evita que el admin tenga que
    // conocer el UUID del usuario de antemano. Sin texto (o un solo caracter) se muestran algunos
    // usuarios existentes a modo de sugerencia, nunca la tabla completa, para no pagar el costo de
    // traer todos los usuarios en cada apertura de la pantalla.
    @Transactional(readOnly = true)
    public List<UserSearchResult> search(String query) {
        String trimmed = query == null ? "" : query.trim();
        List<UserAccount> matches = trimmed.length() < 2
                ? userAccountRepository.findTop10ByOrderByFullNameAsc()
                : userAccountRepository.findTop10ByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrderByFullNameAsc(
                        trimmed, trimmed);
        return matches.stream()
                .map(account -> UserSearchResult.of(account, professionalRepository.existsById(account.getId())))
                .toList();
    }
}
