package com.bookly.backendcf.smoke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test basico: verifica que el ApplicationContext de Spring Boot
 * arranca correctamente y que todos los beans principales estan registrados.
 *
 * Forma parte de la suite de smoke (perfil Maven "smoke") y debe correr
 * antes que el resto de pruebas en el pipeline de CI/CD.
 *
 * Cubre el requisito de calidad: "Smoke tests previos al grueso".
 */
@Tag("smoke")
@DisplayName("Smoke: Arranque de Spring Boot")
@SpringBootTest
class SmokeTest {

    @Test
    @DisplayName("El ApplicationContext carga sin errores")
    void contextLoads(ApplicationContext context) {
        // Arrange - el contexto se carga por la anotacion @SpringBootTest
        // Act
        int beanCount = context.getBeanDefinitionCount();
        // Assert
        assertThat(context)
                .as("El contexto de Spring debe estar activo")
                .isNotNull();
        assertThat(beanCount)
                .as("El contexto debe contener al menos 50 beans registrados")
                .isGreaterThan(50);
    }

    @Test
    @DisplayName("El bean principal BackendcfApplication esta registrado")
    void mainBeanIsRegistered(ApplicationContext context) {
        // Arrange & Assert
        assertThat(context.containsBean("backendcfApplication"))
                .as("El bean de la aplicacion principal debe existir")
                .isTrue();
    }
}