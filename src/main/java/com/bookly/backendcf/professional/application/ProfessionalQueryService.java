package com.bookly.backendcf.professional.application;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.professional.domain.model.Professional;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.professional.presentation.dto.ProfessionalResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfessionalQueryService {

    private final ProfessionalRepository professionalRepository;
    private final UserAccountRepository userAccountRepository;

    public ProfessionalQueryService(
            ProfessionalRepository professionalRepository,
            UserAccountRepository userAccountRepository) {
        this.professionalRepository = professionalRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<ProfessionalResponse> list() {
        List<Professional> professionals = professionalRepository.findAll();
        Map<UUID, UserAccount> accounts = userAccountRepository
                .findAllById(professionals.stream().map(Professional::getId).toList())
                .stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));

        return professionals.stream()
                .map(professional -> ProfessionalResponse.from(accounts.get(professional.getId()), professional))
                .sorted(Comparator.comparing(ProfessionalResponse::fullName))
                .toList();
    }
}
