package com.bookly.backendcf.auth.security;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * MantisBT BUG-005: antes confiaba en el "sub" y el "role" que trae el propio token sin consultar
 * la base. Un token con firma valida pero con un usuario inexistente, deshabilitado o ya eliminado
 * se aceptaba igual. Ahora busca la cuenta y usa su rol actual, no el que el token diga tener.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokenService;
    private final UserAccountRepository accountRepository;

    public JwtAuthenticationFilter(JwtTokenService tokenService, UserAccountRepository accountRepository) {
        this.tokenService = tokenService;
        this.accountRepository = accountRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            JwtTokenService.TokenClaims claims = tokenService.parse(header.substring(7));
            if (claims != null) {
                try {
                    UUID subject = UUID.fromString(claims.subject());
                    UserAccount account = accountRepository.findById(subject).orElse(null);
                    if (account != null && account.isEnabled() && !account.isLocked(OffsetDateTime.now())) {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                claims.subject(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole())));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (IllegalArgumentException ignored) { }
            }
        }
        chain.doFilter(request, response);
    }
}
