package com.bookly.backendcf.auth.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "security.login.ip-max-attempts=2")
class AuthLoginIpLimitTest {

    @Autowired
    private MockMvc mockMvc;

    private static String loginBody(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"Wrong1!pass\"}";
    }

    @Test
    void rotarCorreosDesdeLaMismaIpTerminaEnBloqueo429() throws Exception {
        // La ultima entrada de X-Forwarded-For es la que agrega el proxy; la primera la controla el cliente.
        for (int attempt = 1; attempt <= 2; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "spoofed-" + attempt + ", 203.0.113.7")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("nadie" + attempt + "@example.com")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "otro-spoof, 203.0.113.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nadie3@example.com")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void sinCabeceraUsaLaDireccionRemotaDeLaConexion() throws Exception {
        for (int attempt = 1; attempt <= 2; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.9");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("nadie@example.com")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.9");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nadie@example.com")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void registroConDominioDesechableDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguien@mailinator.com\",\"password\":\"Valid1!pass\","
                                + "\"fullName\":\"Alguien Temporal\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DISPOSABLE_EMAIL_DOMAIN"));
    }
}
