package com.bookly.backendcf.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.auth.domain.events.RoleChangedEvent;
import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.event.RoleEventPublisher;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.presentation.dto.RoleAssignmentRequest;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.shared.security.ActorIdentityProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoleAssignmentServiceTest {

    private final UUID userId = UUID.randomUUID();
    private UserAccountRepository userRepository;
    private ProfessionalRepository professionalRepository;
    private RoleEventPublisher roleEventPublisher;
    private ActorIdentityProvider actorIdentityProvider;
    private RoleAssignmentService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserAccountRepository.class);
        professionalRepository = mock(ProfessionalRepository.class);
        roleEventPublisher = mock(RoleEventPublisher.class);
        actorIdentityProvider = mock(ActorIdentityProvider.class);
        when(actorIdentityProvider.currentActorId()).thenReturn(userId);
        when(userRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new RoleAssignmentService(
                userRepository, professionalRepository, roleEventPublisher, actorIdentityProvider);
    }

    @Test
    void clienteSeConvierteEnProfesionalCreandoSuPerfil() {
        givenUser(UserRole.CUSTOMER);
        when(professionalRepository.existsById(userId)).thenReturn(false);

        var response = service.assign(userId, new RoleAssignmentRequest(UserRole.PROFESSIONAL, "Ortodoncia"));

        assertThat(response.role()).isEqualTo(UserRole.PROFESSIONAL);
        verify(professionalRepository).saveAndFlush(any());
        ArgumentCaptor<RoleChangedEvent> eventCaptor = ArgumentCaptor.forClass(RoleChangedEvent.class);
        verify(roleEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().actorUserId()).isEqualTo(userId);
        assertThat(eventCaptor.getValue().targetUserId()).isEqualTo(userId);
        assertThat(eventCaptor.getValue().previousRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(eventCaptor.getValue().newRole()).isEqualTo(UserRole.PROFESSIONAL);
    }

    @Test
    void clienteAProfesionalSinEspecialidadEsRechazado() {
        givenUser(UserRole.CUSTOMER);
        when(professionalRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> service.assign(userId, new RoleAssignmentRequest(UserRole.PROFESSIONAL, " ")))
                .isInstanceOf(SpecialtyRequiredException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void profesionalConPerfilNoPuedeVolverseCliente() {
        givenUser(UserRole.PROFESSIONAL);
        when(professionalRepository.existsById(userId)).thenReturn(true);

        assertThatThrownBy(() -> service.assign(userId, new RoleAssignmentRequest(UserRole.CUSTOMER, null)))
                .isInstanceOf(ProfessionalProfileExistsException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void mismoRolNoModificaNada() {
        givenUser(UserRole.CUSTOMER);

        service.assign(userId, new RoleAssignmentRequest(UserRole.CUSTOMER, null));

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void usuarioInexistenteDevuelveNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(userId, new RoleAssignmentRequest(UserRole.ADMIN, null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    private void givenUser(UserRole role) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                new UserAccount("usuario@example.com", "hash", "Usuario de prueba", role)));
    }
}
