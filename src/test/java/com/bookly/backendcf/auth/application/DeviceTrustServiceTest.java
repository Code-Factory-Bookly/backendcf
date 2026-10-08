package com.bookly.backendcf.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.auth.domain.model.TrustedDevice;
import com.bookly.backendcf.auth.infrastructure.persistence.TrustedDeviceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeviceTrustServiceTest {

    private TrustedDeviceRepository repository;
    private DeviceTrustService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(TrustedDeviceRepository.class);
        service = new DeviceTrustService(repository);
    }

    @Test
    void issueTokenGuardaUnDispositivoConVigenciaDeTreintaDias() {
        String token = service.issueToken(userId);

        assertThat(token).isNotBlank();
        ArgumentCaptor<TrustedDevice> captor = ArgumentCaptor.forClass(TrustedDevice.class);
        verify(repository).save(captor.capture());
        TrustedDevice saved = captor.getValue();
        assertThat(saved.getTokenHash()).isNotBlank();
        assertThat(saved.isValid(OffsetDateTime.now().plusDays(29))).isTrue();
        assertThat(saved.isValid(OffsetDateTime.now().plusDays(31))).isFalse();
    }

    @Test
    void isTrustedEsFalsoParaUnTokenNuloOVacio() {
        assertThat(service.isTrusted(userId, null)).isFalse();
        assertThat(service.isTrusted(userId, "")).isFalse();
        assertThat(service.isTrusted(userId, "  ")).isFalse();
        verify(repository, never()).findByUserIdAndExpiresAtAfter(any(), any());
    }

    @Test
    void isTrustedEsFalsoSinDispositivosVigentes() {
        when(repository.findByUserIdAndExpiresAtAfter(any(), any())).thenReturn(List.of());

        assertThat(service.isTrusted(userId, "cualquier-token")).isFalse();
    }

    @Test
    void isTrustedEsVerdaderoParaElMismoTokenEmitido() {
        ArgumentCaptor<TrustedDevice> captor = ArgumentCaptor.forClass(TrustedDevice.class);
        String token = service.issueToken(userId);
        verify(repository).save(captor.capture());
        when(repository.findByUserIdAndExpiresAtAfter(any(), any())).thenReturn(List.of(captor.getValue()));

        assertThat(service.isTrusted(userId, token)).isTrue();
        assertThat(service.isTrusted(userId, "token-distinto")).isFalse();
    }

    @Test
    void revokeAllEliminaLosDispositivosDelUsuario() {
        service.revokeAll(userId);

        verify(repository).deleteByUserId(userId);
    }
}
