package com.bookly.backendcf.auth.configuration;

import com.bookly.backendcf.auth.security.JwtAuthenticationFilter;
import com.bookly.backendcf.auth.security.JwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        // Perfil y catálogo: cualquier visitante los consulta, solo ADMIN los modifica.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/organization/profile",
                                "/api/v1/specialties",
                                "/api/v1/services",
                                "/api/v1/services/*").permitAll()
                        .requestMatchers(
                                "/api/v1/organization/profile",
                                "/api/v1/specialties/**",
                                "/api/v1/services/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
