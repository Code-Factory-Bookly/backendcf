package com.bookly.backendcf.professional.application;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.catalog.application.ServiceOfferingNotFoundException;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.professional.domain.model.Professional;
import com.bookly.backendcf.professional.domain.model.ProfessionalServiceAssignment;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalServiceRepository;
import com.bookly.backendcf.professional.presentation.dto.ProfessionalSummary;
import com.bookly.backendcf.schedule.application.ProfessionalNotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfessionalServiceAssignmentService {

    private final ProfessionalServiceRepository assignmentRepository;
    private final ProfessionalRepository professionalRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final UserAccountRepository userAccountRepository;

    public ProfessionalServiceAssignmentService(
            ProfessionalServiceRepository assignmentRepository,
            ProfessionalRepository professionalRepository,
            ServiceOfferingRepository serviceOfferingRepository,
            UserAccountRepository userAccountRepository) {
        this.assignmentRepository = assignmentRepository;
        this.professionalRepository = professionalRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<ProfessionalSummary> listProfessionalsForService(UUID serviceId) {
        if (!serviceOfferingRepository.existsById(serviceId)) {
            throw new ServiceOfferingNotFoundException(serviceId);
        }

        List<UUID> professionalIds = assignmentRepository.findByServiceId(serviceId).stream()
                .map(ProfessionalServiceAssignment::getProfessionalId)
                .toList();
        Map<UUID, UserAccount> accounts = userAccountRepository.findAllById(professionalIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        Map<UUID, Professional> professionals = professionalRepository.findAllById(professionalIds).stream()
                .collect(Collectors.toMap(Professional::getId, Function.identity()));

        return professionalIds.stream()
                .filter(id -> accounts.containsKey(id) && professionals.containsKey(id))
                .map(id -> ProfessionalSummary.from(accounts.get(id), professionals.get(id)))
                .sorted(Comparator.comparing(ProfessionalSummary::fullName))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> listServiceIdsForProfessional(UUID professionalId) {
        requireProfessional(professionalId);
        return assignmentRepository.findByProfessionalId(professionalId).stream()
                .map(ProfessionalServiceAssignment::getServiceId)
                .toList();
    }

    @Transactional
    public void replaceServicesForProfessional(UUID professionalId, List<UUID> serviceIds) {
        requireProfessional(professionalId);
        for (UUID serviceId : serviceIds) {
            if (!serviceOfferingRepository.existsById(serviceId)) {
                throw new ServiceOfferingNotFoundException(serviceId);
            }
        }

        assignmentRepository.deleteByProfessionalId(professionalId);
        assignmentRepository.flush();
        assignmentRepository.saveAllAndFlush(
                serviceIds.stream()
                        .distinct()
                        .map(serviceId -> new ProfessionalServiceAssignment(professionalId, serviceId))
                        .toList());
    }

    private void requireProfessional(UUID professionalId) {
        if (!professionalRepository.existsById(professionalId)) {
            throw new ProfessionalNotFoundException(professionalId);
        }
    }
}
