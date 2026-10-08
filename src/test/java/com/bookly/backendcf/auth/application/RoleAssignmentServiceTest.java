package com.bookly.backendcf.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.presentation.dto.RoleAssignmentRequest;
import com.bookly.backendcf.auth.presentation.dto.UserSearchResult;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleAssignmentServiceTest {

    private final UUID userId = UUID.randomUUID();
    private UserAccountRepository userRepository;
    private ProfessionalRepository professionalRepository;
    private RoleAssignmentService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserAccountRepository.class);
        professionalRepository = mock(ProfessionalRepository.class);
        when(userRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new RoleAssignmentService(userRepository, professionalRepository);
    }

    @Test
    void clienteSeConvierteEnProfesionalCreandoSuPerfil() {
        givenUser(UserRole.CUSTOMER);
        when(professionalRepository.existsById(userId)).thenReturn(false);

        var response = service.assign(userId, new RoleAssignmentRequest(UserRole.PROFESSIONAL, "Ortodoncia"));

        assertThat(response.role()).isEqualTo(UserRole.PROFESSIONAL);
        verify(professionalRepository).saveAndFlush(any());
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

    @Test
    void busquedaSinTextoDevuelveSugerenciasExistentes() {
        UserAccount existing = new UserAccount("sugerido@example.com", "hash", "Sugerido Uno", UserRole.CUSTOMER);
        when(userRepository.findTop10ByOrderByFullNameAsc()).thenReturn(List.of(existing));
        when(professionalRepository.existsById(existing.getId())).thenReturn(false);

        List<UserSearchResult> results = service.search(" ");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).email()).isEqualTo("sugerido@example.com");
        assertThat(results.get(0).hasProfessionalProfile()).isFalse();
        verify(userRepository, never())
                .findTop10ByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrderByFullNameAsc(any(), any());
    }

    @Test
    void busquedaConTextoFiltraPorCorreoONombreYMarcaPerfilProfesional() {
        UserAccount professional = new UserAccount("pro@example.com", "hash", "Pro Uno", UserRole.PROFESSIONAL);
        when(userRepository.findTop10ByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrderByFullNameAsc(
                        "pro", "pro"))
                .thenReturn(List.of(professional));
        when(professionalRepository.existsById(professional.getId())).thenReturn(true);

        List<UserSearchResult> results = service.search("pro");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).hasProfessionalProfile()).isTrue();
        verify(userRepository, never()).findTop10ByOrderByFullNameAsc();
    }

    @Test
    void busquedaConUnSoloCaracterSeTrataComoSinTexto() {
        when(userRepository.findTop10ByOrderByFullNameAsc()).thenReturn(List.of());

        List<UserSearchResult> results = service.search("a");

        assertThat(results).isEmpty();
        verify(userRepository).findTop10ByOrderByFullNameAsc();
    }

    private void givenUser(UserRole role) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                new UserAccount("usuario@example.com", "hash", "Usuario de prueba", role)));
    }
}
