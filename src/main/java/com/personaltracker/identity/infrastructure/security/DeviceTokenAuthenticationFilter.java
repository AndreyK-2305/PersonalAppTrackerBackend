package com.personaltracker.identity.infrastructure.security;

import com.personaltracker.identity.application.usecase.DeviceTokens;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica las peticiones de dispositivo con {@code Authorization: Bearer <secreto>} (ADR-0017).
 * Un token válido deja como principal al dueño ({@link AuthenticatedUser}, así funciona
 * {@code @CurrentUser}) con una autoridad {@code SCOPE_<permiso>} por permiso. Un token ausente o
 * inválido deja la petición sin autenticar y termina en 401. No registra el secreto.
 *
 * <p>No es un bean: si lo fuera, Spring Boot lo registraría también como filtro de toda la aplicación.
 */
class DeviceTokenAuthenticationFilter extends OncePerRequestFilter {

    static final String SCOPE_PREFIX = "SCOPE_";
    private static final String BEARER = "Bearer ";

    private final DeviceTokens deviceTokens;

    DeviceTokenAuthenticationFilter(DeviceTokens deviceTokens) {
        this.deviceTokens = deviceTokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            deviceTokens.authenticate(header.substring(BEARER.length()).trim()).ifPresent(authenticated -> {
                List<SimpleGrantedAuthority> authorities = authenticated.scopes().stream()
                        .map(scope -> new SimpleGrantedAuthority(SCOPE_PREFIX + scope.value()))
                        .toList();
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(authenticated.owner().value()), null, authorities));
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
