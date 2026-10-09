package com.bookly.backendcf.auth.configuration;

import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtAuthenticationFilter;
import com.bookly.backendcf.auth.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Configuration
public class SecurityConfiguration {

    private final JwtTokenService tokenService;
    private final UserAccountRepository accountRepository;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    public SecurityConfiguration(JwtTokenService tokenService, UserAccountRepository accountRepository) {
        this.tokenService = tokenService;
        this.accountRepository = accountRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, 401, "UNAUTHORIZED", "Se requiere autenticación"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, 403, "ACCESS_DENIED", "No tiene permisos para este recurso")))
                .authorizeHttpRequests(authorize -> authorize
        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
        // MFA: el mfaToken viaja en el cuerpo y lo valida MfaService, no el filtro JWT
        .requestMatchers("/api/v1/auth/mfa/**").permitAll()

        // Aprovisionamiento inicial: público y de un solo uso
        .requestMatchers(HttpMethod.POST, "/api/v1/platform/setup").permitAll()

        // Catálogo: cualquiera puede consultar; solo ADMIN modifica
        .requestMatchers(HttpMethod.GET, "/api/v1/servicios", "/api/v1/servicios/**").permitAll()
        .requestMatchers("/api/v1/servicios/**").hasRole("ADMIN")

        // Profesionales: solo ADMIN los registra y gestiona
        .requestMatchers("/api/v1/profesionales/**").hasRole("ADMIN")

        // Asignación de roles y reseteo de clave por ADMIN (HU-17 + parche sin HU-10)
        .requestMatchers("/api/v1/usuarios/**").hasRole("ADMIN")

        // Ajustes de MFA del propio usuario autenticado (no el mfaToken de login)
        .requestMatchers("/api/v1/mfa/**").hasRole("ADMIN")
        // Auditoría: solo ADMIN (HU-19)
        .requestMatchers(HttpMethod.GET, "/api/v1/auditoria", "/api/v1/auditoria/**").hasRole("ADMIN")
        // Horario semanal: solo el profesional dueño (o ADMIN) lo consulta y modifica
        .requestMatchers("/api/v1/horarios/**").hasAnyRole("PROFESSIONAL", "ADMIN")

        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenService, accountRepository), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
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
