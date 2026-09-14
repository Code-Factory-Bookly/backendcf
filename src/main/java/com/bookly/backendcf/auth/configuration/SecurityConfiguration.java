package com.bookly.backendcf.auth.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/register").permitAll()
                        // Auto-registro de organización: se ejecuta sin tenant en contexto, porque
                        // la organización aún no existe. En HU-21 debe quedar exenta del TenantFilter.
                        .requestMatchers(HttpMethod.POST, "/api/v1/organizations").permitAll()
                        .anyRequest().authenticated());

        return http.build();
    }
}
