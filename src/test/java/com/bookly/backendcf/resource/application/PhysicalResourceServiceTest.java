
package com.bookly.backendcf.resource.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.resource.domain.model.PhysicalResource;
import com.bookly.backendcf.resource.infrastructure.persistence.PhysicalResourceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PhysicalResourceServiceTest {

    @Mock
    private PhysicalResourceRepository repository;

    private PhysicalResourceService service;

    @BeforeEach
    void setUp() {
        service = new PhysicalResourceService(repository);
    }

    @Test
    void crearRecursoValidoGuardaInventario() {
        when(repository.save(any(PhysicalResource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalResource result = service.create("Cabina", 3);

        assertEquals("Cabina", result.getName());
        assertEquals(3, result.getTotalQuantity());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        ArgumentCaptor<PhysicalResource> saved =
                ArgumentCaptor.forClass(PhysicalResource.class);

        verify(repository).save(saved.capture());
        assertEquals(3, saved.getValue().getTotalQuantity());
    }

    @Test
    void crearRecursoNormalizaEspacios() {
        when(repository.save(any(PhysicalResource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalResource result =
                service.create("  Cabina    odontologica  ", 2);

        assertEquals("Cabina odontologica", result.getName());
        verify(repository)
                .existsByNameIgnoreCase("Cabina odontologica");
    }

    @Test
    void crearRecursoDuplicadoEsRechazado() {
        when(repository.existsByNameIgnoreCase("Cabina"))
                .thenReturn(true);

        assertThrows(
                PhysicalResourceAlreadyExistsException.class,
                () -> service.create("Cabina", 3)
        );

        verify(repository, never()).save(any());
    }

    @Test
    void crearRecursoConCantidadCeroEsRechazado() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create("Cabina", 0)
        );

        verify(repository, never()).save(any());
    }

    @Test
    void crearRecursoSinNombreDevuelveError400() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create("   ", 3)
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(repository, never()).save(any());
    }

    @Test
    void consultarRecursoInexistenteEsRechazado() {
        UUID id = UUID.randomUUID();

        when(repository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                PhysicalResourceNotFoundException.class,
                () -> service.get(id)
        );
    }

    @Test
    void actualizarRecursoModificaNombreYCantidad() {
        UUID id = UUID.randomUUID();
        PhysicalResource resource = new PhysicalResource("Cabina", 2);

        when(repository.findById(id))
                .thenReturn(Optional.of(resource));

        PhysicalResource result =
                service.update(id, "  Cabina   premium ", 5);

        assertEquals("Cabina premium", result.getName());
        assertEquals(5, result.getTotalQuantity());

        verify(repository)
                .existsByNameIgnoreCaseAndIdNot(
                        "Cabina premium", id
                );
    }

    @Test
    void listarRecursosDevuelveResultadoDelRepositorio() {
        PhysicalResource resource =
                new PhysicalResource("Cabina", 3);

        when(repository.findAllByOrderByNameAsc())
                .thenReturn(List.of(resource));

        List<PhysicalResource> result = service.list();

        assertEquals(1, result.size());
        assertEquals("Cabina", result.get(0).getName());
        assertTrue(result.get(0).getTotalQuantity() > 0);
    }
}
