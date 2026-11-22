
package com.bookly.backendcf.resource.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.resource.application.PhysicalResourceAlreadyExistsException;
import com.bookly.backendcf.resource.application.PhysicalResourceNotFoundException;
import com.bookly.backendcf.resource.application.PhysicalResourceService;
import com.bookly.backendcf.resource.domain.model.PhysicalResource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = PhysicalResourceController.class)
@AutoConfigureMockMvc(addFilters = false)
class PhysicalResourceControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private PhysicalResourceService service;

    @Test
    void crearRecursoValidoDevuelve201() {
        PhysicalResource resource = mock(PhysicalResource.class);

        when(resource.getName()).thenReturn("Cabina");
        when(resource.getTotalQuantity()).thenReturn(3);
        when(service.create("Cabina", 3)).thenReturn(resource);

        mockMvcTester.post()
                .uri("/api/v1/recursos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Cabina",
                          "totalQuantity": 3
                        }
                        """)
                .assertThat()
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.totalQuantity")
                .isEqualTo(3);
    }

    @Test
    void crearRecursoSinNombreDevuelve400() {
        mockMvcTester.post()
                .uri("/api/v1/recursos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "",
                          "totalQuantity": 3
                        }
                        """)
                .assertThat()
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void crearRecursoConCantidadCeroDevuelve400() {
        mockMvcTester.post()
                .uri("/api/v1/recursos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Cabina",
                          "totalQuantity": 0
                        }
                        """)
                .assertThat()
                .hasStatus(400);
    }

    @Test
    void crearRecursoDuplicadoDevuelve409() {
        when(service.create(eq("Cabina"), eq(3)))
                .thenThrow(
                        new PhysicalResourceAlreadyExistsException("Cabina")
                );

        mockMvcTester.post()
                .uri("/api/v1/recursos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Cabina",
                          "totalQuantity": 3
                        }
                        """)
                .assertThat()
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("PHYSICAL_RESOURCE_ALREADY_EXISTS");
    }

    @Test
    void consultarRecursoInexistenteDevuelve404() {
        UUID id = UUID.randomUUID();

        when(service.get(id))
                .thenThrow(new PhysicalResourceNotFoundException(id));

        mockMvcTester.get()
                .uri("/api/v1/recursos/{id}", id)
                .assertThat()
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("PHYSICAL_RESOURCE_NOT_FOUND");
    }

    @Test
    void listarRecursosDevuelve200() {
        PhysicalResource resource = mock(PhysicalResource.class);

        when(resource.getName()).thenReturn("Cabina");
        when(resource.getTotalQuantity()).thenReturn(3);
        when(service.list()).thenReturn(List.of(resource));

        mockMvcTester.get()
                .uri("/api/v1/recursos")
                .assertThat()
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$[0].name")
                .isEqualTo("Cabina");
    }

    @Test
    void consultarRecursoExistenteDevuelve200() {
        UUID id = UUID.randomUUID();
        PhysicalResource resource = mock(PhysicalResource.class);

        when(resource.getId()).thenReturn(id);
        when(resource.getName()).thenReturn("Cabina");
        when(resource.getTotalQuantity()).thenReturn(3);
        when(service.get(id)).thenReturn(resource);

        mockMvcTester.get()
                .uri("/api/v1/recursos/{id}", id)
                .assertThat()
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.id")
                .isEqualTo(id.toString());
    }

    @Test
    void actualizarRecursoValidoDevuelve200() {
        UUID id = UUID.randomUUID();
        PhysicalResource resource = mock(PhysicalResource.class);

        when(resource.getId()).thenReturn(id);
        when(resource.getName()).thenReturn("Cabina premium");
        when(resource.getTotalQuantity()).thenReturn(5);

        when(service.update(id, "Cabina premium", 5))
                .thenReturn(resource);

        mockMvcTester.put()
                .uri("/api/v1/recursos/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Cabina premium",
                          "totalQuantity": 5
                        }
                        """)
                .assertThat()
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.totalQuantity")
                .isEqualTo(5);
    }

    @Test
    void actualizarRecursoConCantidadInvalidaDevuelve400() {
        UUID id = UUID.randomUUID();

        mockMvcTester.put()
                .uri("/api/v1/recursos/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Cabina",
                          "totalQuantity": 0
                        }
                        """)
                .assertThat()
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void actualizarRecursoInexistenteDevuelve404() {
        UUID id = UUID.randomUUID();

        when(service.update(id, "Cabina", 3))
                .thenThrow(new PhysicalResourceNotFoundException(id));

        mockMvcTester.put()
                .uri("/api/v1/recursos/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Cabina",
                          "totalQuantity": 3
                        }
                        """)
                .assertThat()
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("PHYSICAL_RESOURCE_NOT_FOUND");
    }
}
