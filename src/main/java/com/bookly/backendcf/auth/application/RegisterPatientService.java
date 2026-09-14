package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.presentation.dto.RegisterPatientRequest;
import com.bookly.backendcf.auth.presentation.dto.RegisterPatientResponse;
import com.bookly.backendcf.organization.application.OrganizationNotFoundException;
import com.bookly.backendcf.organization.infrastructure.persistence.OrganizationRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterPatientService {

    private final UserAccountRepository userAccountRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterPatientService(
            UserAccountRepository userAccountRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterPatientResponse register(RegisterPatientRequest request) {
        UUID tenantId = request.organizationId();

        if (!organizationRepository.existsById(tenantId)) {
            throw new OrganizationNotFoundException(tenantId);
        }

        String normalizedEmail = normalizeEmail(request.email());

        // La unicidad del correo es por organización: el mismo correo puede existir en dos
        // negocios distintos sin relación entre sí.
        if (userAccountRepository.existsByTenantIdAndEmail(tenantId, normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        UserAccount userAccount = new UserAccount(
                tenantId,
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                normalizeFullName(request.fullName()),
                UserRole.PATIENT);

        return RegisterPatientResponse.from(userAccountRepository.save(userAccount));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String normalizeFullName(String fullName) {
        return fullName.trim().replaceAll("\\s+", " ");
    }
}
