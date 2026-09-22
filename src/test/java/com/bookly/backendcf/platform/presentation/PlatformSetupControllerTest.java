package com.bookly.backendcf.platform.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.platform.application.PlatformAlreadyConfiguredException;
import com.bookly.backendcf.platform.application.PlatformSetupService;
import com.bookly.backendcf.platform.presentation.dto.PlatformSetupResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Contrato HTTP de HU-20: verifica los códigos y cuerpos de respuesta descritos literalmente en
 * los escenarios Gherkin (201/409/400), no solo la lógica de negocio. La cadena de seguridad se
 * desactiva porque el endpoint es público (SecurityConfiguration ya lo declara permitAll) y no es
 * el objeto de esta prueba.
 */
@WebMvcTest(controllers = PlatformSetupController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlatformSetupControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private PlatformSetupService platformSetupService;

    // HU-20 - Escenario Gherkin "Aprovisionamiento exitoso" a nivel de contrato HTTP: el POST
    // debe responder 201 con el identificador de la plataforma en el body JSON.
    @Test
    void aprovisionamientoExitosoDevuelve201ConElIdentificadorDeLaPlataforma() {
        UUID platformId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        when(platformSetupService.setup(any()))
                .thenReturn(new PlatformSetupResponse(platformId, "Bookly Salud", adminUserId));

        mockMvcTester.post().uri("/api/v1/platform/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Bookly Salud",
                          "adminEmail": "admin@bookly.com",
                          "adminPassword": "Secreta123*",
                          "adminFullName": "Ada Admin"
                        }
                        """)
                .assertThat()
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.platformId").isEqualTo(platformId.toString());
    }

    // HU-20 - Escenario Gherkin "Rechazo de un segundo aprovisionamiento" a nivel de contrato
    // HTTP: debe responder 409 con errorCode "PLATFORM_ALREADY_CONFIGURED" en el body JSON.
    @Test
    void segundoAprovisionamientoDevuelve409ConErrorCodePlatformAlreadyConfigured() {
        when(platformSetupService.setup(any())).thenThrow(new PlatformAlreadyConfiguredException());

        mockMvcTester.post().uri("/api/v1/platform/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Otra Plataforma",
                          "adminEmail": "otro@bookly.com",
                          "adminPassword": "Secreta123*",
                          "adminFullName": "Otro Admin"
                        }
                        """)
                .assertThat()
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.errorCode").isEqualTo("PLATFORM_ALREADY_CONFIGURED");
    }

    // HU-20 - Escenario Gherkin "Rechazo por datos incompletos" a nivel de contrato HTTP: sin
    // "name", debe responder 400 con errorCode, message, details y traceId en el body JSON.
    @Test
    void requestSinNombreDevuelve400ConElContratoDeErrorCompleto() {
        mockMvcTester.post().uri("/api/v1/platform/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "adminEmail": "admin@bookly.com",
                          "adminPassword": "Secreta123*",
                          "adminFullName": "Ada Admin"
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
}
