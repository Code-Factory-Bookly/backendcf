package com.bookly.backendcf.smoke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test de endpoints publicos: verifica que los endpoints que
 * no requieren autenticacion responden correctamente sin necesidad
 * de sembrar datos.
 *
 * Es parte de la suite de smoke (perfil Maven "smoke") y debe
 * ejecutarse antes que las pruebas de integracion pesadas.
 */
@Tag("smoke")
@DisplayName("Smoke: Endpoints publicos")
@SpringBootTest
@AutoConfigureMockMvc
class PublicEndpointsSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/servicios responde 200 con lista (posiblemente vacia)")
    void getServiciosReturnsOk() throws Exception {
        // Arrange - el endpoint /api/v1/servicios es publico
        // Act & Assert
        mockMvc.perform(get("/api/v1/servicios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.nullValue(),
                        org.hamcrest.Matchers.hasSize(greaterThanOrEqualTo(0))))));
    }

    @Test
    @DisplayName("Endpoints protegidos devuelven 401 sin token")
    void protectedEndpointsRequireAuth() throws Exception {
        // Arrange - /api/v1/reservas requiere CUSTOMER o ADMIN
        // Act & Assert
        mockMvc.perform(get("/api/v1/reservas/cliente/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/servicios/{uuid-inexistente} devuelve 404 con errorCode")
    void getServicioInexistenteReturnsNotFound() throws Exception {
        // Arrange - UUID valido pero sin servicio asociado
        // Act & Assert
        mockMvc.perform(get("/api/v1/servicios/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}