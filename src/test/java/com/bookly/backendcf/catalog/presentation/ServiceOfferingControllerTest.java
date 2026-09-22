package com.bookly.backendcf.catalog.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.catalog.application.ServiceOfferingAlreadyExistsException;
import com.bookly.backendcf.catalog.application.ServiceOfferingService;
import com.bookly.backendcf.catalog.domain.model.ServiceStatus;
import com.bookly.backendcf.catalog.presentation.dto.ServiceOfferingResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Contrato HTTP de HU-02: verifica los códigos y cuerpos de respuesta de los escenarios Gherkin
 * (201/409/400/200), no solo la lógica de negocio (ya cubierta en ServiceOfferingServiceTest). La
 * cadena de seguridad se desactiva porque el objeto de esta prueba es el contrato, no la
 * autorización (esa la impone SecurityConfiguration: GET público, resto solo ADMIN).
 */
@WebMvcTest(controllers = ServiceOfferingController.class)
@AutoConfigureMockMvc(addFilters = false)
class ServiceOfferingControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private ServiceOfferingService serviceOfferingService;

    // HU-02 - Escenario Gherkin "Registro correcto de un servicio en el catálogo" a nivel de
    // contrato HTTP: el POST debe responder 201 con el servicio creado en el body JSON.
    @Test
    void registroCorrectoDevuelve201ConElServicioCreado() {
        UUID id = UUID.randomUUID();
        when(serviceOfferingService.create(any())).thenReturn(new ServiceOfferingResponse(
                id, "Limpieza dental", "Profesional", "Odontología", 45, BigDecimal.valueOf(80000),
                ServiceStatus.ACTIVO));

        mockMvcTester.post().uri("/api/v1/servicios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Limpieza dental",
                          "description": "Profesional",
                          "category": "Odontología",
                          "durationMinutes": 45,
                          "price": 80000
                        }
                        """)
                .assertThat()
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.id").isEqualTo(id.toString());
    }

    // HU-02 - Caso de duplicado a nivel de contrato HTTP: si ya existe un servicio con ese nombre,
    // debe responder 409 con errorCode "SERVICE_ALREADY_EXISTS".
    @Test
    void registroConNombreDuplicadoDevuelve409ConErrorCodeServiceAlreadyExists() {
        when(serviceOfferingService.create(any()))
                .thenThrow(new ServiceOfferingAlreadyExistsException("Manicure"));

        mockMvcTester.post().uri("/api/v1/servicios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Manicure",
                          "category": "Belleza",
                          "durationMinutes": 30,
                          "price": 20000
                        }
                        """)
                .assertThat()
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.errorCode").isEqualTo("SERVICE_ALREADY_EXISTS");
    }

    // HU-02 - Escenario Gherkin "Intento de registrar un servicio con datos obligatorios
    // faltantes" a nivel de contrato HTTP: sin "name", "category", "durationMinutes" ni "price",
    // debe responder 400 con el contrato de error completo y los campos pendientes en "details".
    @Test
    void registroConCamposObligatoriosFaltantesDevuelve400ConLosCamposPendientes() {
        mockMvcTester.post().uri("/api/v1/servicios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "description": "Sin datos obligatorios"
                        }
                        """)
                .assertThat()
                .hasStatus(400)
                .bodyJson()
                .hasPath("$.errorCode")
                .hasPath("$.message")
                .hasPath("$.details")
                .hasPath("$.traceId")
                .extractingPath("$.errorCode").isEqualTo("VALIDATION_ERROR");
    }

    // HU-02 - Escenario Gherkin "Consulta pública del catálogo" a nivel de contrato HTTP: el GET
    // debe responder 200 con la lista de servicios disponibles.
    @Test
    void consultaDelCatalogoDevuelve200ConLosServiciosDisponibles() {
        UUID id = UUID.randomUUID();
        when(serviceOfferingService.list(null)).thenReturn(List.of(new ServiceOfferingResponse(
                id, "Corte de cabello", null, "Peluquería", 30, BigDecimal.valueOf(15000), ServiceStatus.ACTIVO)));

        mockMvcTester.get().uri("/api/v1/servicios")
                .assertThat()
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Corte de cabello");
    }

    // HU-02 - Escenario Gherkin "Consulta pública del catálogo": "puedo filtrar los resultados por
    // categoría" a nivel de contrato HTTP.
    @Test
    void consultaDelCatalogoConFiltroDeCategoriaDevuelve200ConLosServiciosDeEsaCategoria() {
        UUID id = UUID.randomUUID();
        when(serviceOfferingService.list("Odontología")).thenReturn(List.of(new ServiceOfferingResponse(
                id, "Limpieza dental", null, "Odontología", 45, BigDecimal.valueOf(80000), ServiceStatus.ACTIVO)));

        mockMvcTester.get().uri("/api/v1/servicios?categoria=Odontología")
                .assertThat()
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$[0].category").isEqualTo("Odontología");
    }
}
