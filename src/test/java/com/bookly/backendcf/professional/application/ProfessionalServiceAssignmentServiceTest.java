package com.bookly.backendcf.professional.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.catalog.application.ServiceOfferingNotFoundException;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.professional.domain.model.Professional;
import com.bookly.backendcf.professional.domain.model.ProfessionalServiceAssignment;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalServiceRepository;
import com.bookly.backendcf.schedule.application.ProfessionalNotFoundException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProfessionalServiceAssignmentServiceTest {

    private final UUID professionalId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    private ProfessionalServiceRepository assignmentRepository;
    private ProfessionalRepository professionalRepository;
    private ServiceOfferingRepository serviceOfferingRepository;
    private UserAccountRepository userAccountRepository;
    private ProfessionalServiceAssignmentService service;

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(ProfessionalServiceRepository.class);
        professionalRepository = mock(ProfessionalRepository.class);
        serviceOfferingRepository = mock(ServiceOfferingRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        service = new ProfessionalServiceAssignmentService(
                assignmentRepository, professionalRepository, serviceOfferingRepository, userAccountRepository);
    }

    @Test
    void listaProfesionalesQueOfrecenElServicio() throws Exception {
        when(serviceOfferingRepository.existsById(serviceId)).thenReturn(true);
        when(assignmentRepository.findByServiceId(serviceId))
                .thenReturn(List.of(new ProfessionalServiceAssignment(professionalId, serviceId)));
        when(userAccountRepository.findAllById(List.of(professionalId)))
                .thenReturn(List.of(account("Zoe Prueba")));
        when(professionalRepository.findAllById(List.of(professionalId)))
                .thenReturn(List.of(new Professional(professionalId, "Ortodoncia")));

        var result = service.listProfessionalsForService(serviceId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Zoe Prueba");
        assertThat(result.get(0).specialty()).isEqualTo("Ortodoncia");
    }

    @Test
    void servicioInexistenteDevuelveNotFound() {
        when(serviceOfferingRepository.existsById(serviceId)).thenReturn(false);

        assertThatThrownBy(() -> service.listProfessionalsForService(serviceId))
                .isInstanceOf(ServiceOfferingNotFoundException.class);
    }

    @Test
    void reemplazaLosServiciosDeUnProfesional() {
        when(professionalRepository.existsById(professionalId)).thenReturn(true);
        when(serviceOfferingRepository.existsById(serviceId)).thenReturn(true);

        service.replaceServicesForProfessional(professionalId, List.of(serviceId));

        verify(assignmentRepository).deleteByProfessionalId(professionalId);
        verify(assignmentRepository).saveAllAndFlush(anyList());
    }

    @Test
    void profesionalInexistenteAlReemplazarDevuelveNotFound() {
        when(professionalRepository.existsById(professionalId)).thenReturn(false);

        assertThatThrownBy(() -> service.replaceServicesForProfessional(professionalId, List.of(serviceId)))
                .isInstanceOf(ProfessionalNotFoundException.class);
        verify(assignmentRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void servicioInexistenteAlReemplazarDevuelveNotFound() {
        when(professionalRepository.existsById(professionalId)).thenReturn(true);
        when(serviceOfferingRepository.existsById(serviceId)).thenReturn(false);

        assertThatThrownBy(() -> service.replaceServicesForProfessional(professionalId, List.of(serviceId)))
                .isInstanceOf(ServiceOfferingNotFoundException.class);
        verify(assignmentRepository, never()).saveAllAndFlush(anyList());
    }

    private UserAccount account(String fullName) throws Exception {
        UserAccount account = new UserAccount("x@example.com", "hash", fullName, UserRole.PROFESSIONAL);
        Field idField = UserAccount.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(account, professionalId);
        return account;
    }
}
