package com.bookly.backendcf.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MantisBT BUG-002: con la cadena de seguridad real activa (a diferencia de los
 * {@code @WebMvcTest(addFilters = false)}), un path variable con formato invalido terminaba
 * reenviado a /error y rechazado con 401 en vez de 400. Necesita el contexto completo para
 * reproducir el reenvio real.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unUuidMalformadoEnUnaRutaPublicaDevuelve400YNo401() throws Exception {
        mockMvc.perform(get("/api/v1/servicios/no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
