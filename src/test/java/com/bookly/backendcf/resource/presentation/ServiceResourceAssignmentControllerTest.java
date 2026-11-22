
package com.bookly.backendcf.resource.presentation;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.resource.application.PhysicalResourceNotFoundException;
import com.bookly.backendcf.resource.application.ServiceResourceAssignmentService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(controllers = ServiceResourceAssignmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class ServiceResourceAssignmentControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private ServiceResourceAssignmentService service;

    @Test
    void asociarRecursoDevuelve201() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        mockMvcTester.post()
                .uri("/api/v1/recursos/asignaciones/servicios/{serviceId}/recursos/{resourceId}",
                        serviceId, resourceId)
                .assertThat()
                .hasStatus(201);
    }

    @Test
    void consultarRecursosAsociadosDevuelve200() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        when(service.listResources(serviceId))
                .thenReturn(List.of(resourceId));

        mockMvcTester.get()
                .uri("/api/v1/recursos/asignaciones/servicios/{serviceId}", serviceId)
                .assertThat()
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$[0]")
                .isEqualTo(resourceId.toString());
    }

    @Test
    void eliminarAsociacionDevuelve204() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        mockMvcTester.delete()
                .uri("/api/v1/recursos/asignaciones/servicios/{serviceId}/recursos/{resourceId}",
                        serviceId, resourceId)
                .assertThat()
                .hasStatus(204);
    }

    @Test
    void asociarRecursoInexistenteDevuelve404() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        doThrow(new PhysicalResourceNotFoundException(resourceId))
                .when(service).assign(serviceId, resourceId);

        mockMvcTester.post()
                .uri("/api/v1/recursos/asignaciones/servicios/{serviceId}/recursos/{resourceId}",
                        serviceId, resourceId)
                .assertThat()
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("PHYSICAL_RESOURCE_NOT_FOUND");
    }

    @Test
    void asociacionDuplicadaDevuelve409() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        doThrow(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El recurso ya está asociado a este servicio"))
                .when(service).assign(serviceId, resourceId);

        mockMvcTester.post()
                .uri("/api/v1/recursos/asignaciones/servicios/{serviceId}/recursos/{resourceId}",
                        serviceId, resourceId)
                .assertThat()
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("CONFLICT");
    }

    @Test
    void eliminarAsociacionInexistenteDevuelve404() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        doThrow(new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe la asociación entre el servicio y el recurso"))
                .when(service).remove(serviceId, resourceId);

        mockMvcTester.delete()
                .uri("/api/v1/recursos/asignaciones/servicios/{serviceId}/recursos/{resourceId}",
                        serviceId, resourceId)
                .assertThat()
                .hasStatus(404);
    }
}
