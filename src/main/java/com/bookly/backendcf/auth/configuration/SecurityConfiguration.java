package com.bookly.backendcf.auth.configuration;

import com.bookly.backendcf.auth.security.JwtAuthenticationFilter;
import com.bookly.backendcf.auth.security.JwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;

@Configuration
public class SecurityConfiguration {

    private final JwtTokenService tokenService;

    public SecurityConfiguration(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, 401, "UNAUTHORIZED", "Se requiere autenticación"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, 403, "ACCESS_DENIED", "No tiene permisos para este recurso")))
                .authorizeHttpRequests(authorize -> authorize
        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()

        // Aprovisionamiento inicial: público y de un solo uso
        .requestMatchers(HttpMethod.POST, "/api/v1/platform/setup").permitAll()

        // Catálogo: cualquiera puede consultar; solo ADMIN modifica
        .requestMatchers(HttpMethod.GET, "/api/v1/servicios", "/api/v1/servicios/**").permitAll()
        .requestMatchers("/api/v1/servicios/**").hasRole("ADMIN")

        // Profesionales: solo ADMIN los registra y gestiona
        .requestMatchers("/api/v1/profesionales/**").hasRole("ADMIN")

        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeSecurityError(jakarta.servlet.http.HttpServletResponse response,
                                    int status, String errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"errorCode\":\"" + errorCode + "\","
                + "\"message\":\"" + message + "\",\"details\":{},"
                + "\"traceId\":\"" + UUID.randomUUID() + "\","
                + "\"timestamp\":\"" + OffsetDateTime.now() + "\"}");
    }
}
