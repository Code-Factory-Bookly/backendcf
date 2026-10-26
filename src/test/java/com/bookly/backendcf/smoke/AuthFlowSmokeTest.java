package com.bookly.backendcf.smoke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test del flujo de autenticacion basico: verifica que
 * el endpoint de registro y login responde correctamente, sin
 * requerir una BD poblada con datos previos.
 *
 * Como parte de la suite "smoke", solo valida la disponibilidad
 * del endpoint y la forma de la respuesta, no casos funcionales.
 */
@Tag("smoke")
@DisplayName("Smoke: Flujo de autenticacion")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("smoke")
class AuthFlowSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/v1/auth/register responde 201 con usuario creado")
    void registerReturnsCreated() throws Exception {
        // Arrange - payload minimo valido
        String body = """
                {
                  "email": "smoke.user@bookly.local",
                  "password": "Smoke123!Pass",
                  "fullName": "Smoke Test User"
                }
                """;
        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email", is("smoke.user@bookly.local")))
                .andExpect(jsonPath("$.role", is("CUSTOMER")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login responde 401 con credenciales invalidas")
    void loginWithBadCredentialsReturnsUnauthorized() throws Exception {
        // Arrange - credenciales claramente invalidas
        String body = """
                {
                  "email": "noexiste.tal@bookly.local",
                  "password": "WrongPass123!"
                }
                """;
        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}