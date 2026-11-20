
package com.bookly.backendcf.resource.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.resource.domain.model.ServiceResource;
import com.bookly.backendcf.resource.domain.model.ServiceResourceId;
import com.bookly.backendcf.resource.infrastructure.persistence.PhysicalResourceRepository;
import com.bookly.backendcf.resource.infrastructure.persistence.ServiceResourceRepository;
import java.util.List;
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
class ServiceResourceAssignmentServiceTest {

    @Mock
    private ServiceResourceRepository assignmentRepository;

    @Mock
    private ServiceOfferingRepository serviceRepository;

    @Mock
    private PhysicalResourceRepository resourceRepository;

    private ServiceResourceAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new ServiceResourceAssignmentService(
                assignmentRepository,
                serviceRepository,
                resourceRepository
        );
    }

    @Test
    void asociarRecursoValidoGuardaLaRelacion() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        when(serviceRepository.existsById(serviceId)).thenReturn(true);
        when(resourceRepository.existsById(resourceId)).thenReturn(true);

        service.assign(serviceId, resourceId);

        ArgumentCaptor<ServiceResource> captor =
                ArgumentCaptor.forClass(ServiceResource.class);

        verify(assignmentRepository).save(captor.capture());

        assertEquals(serviceId, captor.getValue().getServiceId());
        assertEquals(resourceId, captor.getValue().getResourceId());
    }

    @Test
    void asociarConServicioInexistenteDevuelve404() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.assign(serviceId, resourceId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void asociarConRecursoInexistenteEsRechazado() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        when(serviceRepository.existsById(serviceId)).thenReturn(true);

        assertThrows(
                PhysicalResourceNotFoundException.class,
                () -> service.assign(serviceId, resourceId)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void asociacionDuplicadaDevuelve409() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        when(serviceRepository.existsById(serviceId)).thenReturn(true);
        when(resourceRepository.existsById(resourceId)).thenReturn(true);
        when(assignmentRepository.existsByServiceIdAndResourceId(
                serviceId, resourceId
        )).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.assign(serviceId, resourceId)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void consultarAsociacionesDevuelveRecursosDelServicio() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        when(serviceRepository.existsById(serviceId)).thenReturn(true);
        when(assignmentRepository.findByServiceId(serviceId))
                .thenReturn(List.of(new ServiceResource(serviceId, resourceId)));

        List<UUID> result = service.listResources(serviceId);

        assertEquals(List.of(resourceId), result);
    }

    @Test
    void consultarAsociacionesDeServicioInexistenteDevuelve404() {
        UUID serviceId = UUID.randomUUID();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.listResources(serviceId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(assignmentRepository, never()).findByServiceId(any());
    }

    @Test
    void eliminarAsociacionExistente() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        ServiceResourceId id = new ServiceResourceId(serviceId, resourceId);

        when(assignmentRepository.existsById(id)).thenReturn(true);

        service.remove(serviceId, resourceId);

        verify(assignmentRepository).deleteById(id);
    }

    @Test
    void eliminarAsociacionInexistenteDevuelve404() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        ServiceResourceId id = new ServiceResourceId(serviceId, resourceId);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.remove(serviceId, resourceId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(assignmentRepository, never()).deleteById(id);
    }
}
