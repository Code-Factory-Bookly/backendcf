package com.bookly.backendcf.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokenService;

    public JwtAuthenticationFilter(JwtTokenService tokenService) { this.tokenService = tokenService; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            JwtTokenService.TokenClaims claims = tokenService.parse(header.substring(7));
            if (claims != null) {
                try {
                    UUID.fromString(claims.subject());
                    var authentication = new UsernamePasswordAuthenticationToken(
                            claims.subject(), null, java.util.List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        chain.doFilter(request, response);
    }
}
