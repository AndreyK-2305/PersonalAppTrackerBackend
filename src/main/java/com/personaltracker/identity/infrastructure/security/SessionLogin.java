package com.personaltracker.identity.infrastructure.security;

import com.personaltracker.shared.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * Abre la sesión del usuario ya autenticado por el caso de uso: guarda el contexto de seguridad en
 * la sesión (Spring Session JDBC), cambia el identificador de sesión para evitar la fijación de
 * sesión y emite un token CSRF nuevo (ADR-0004).
 */
@Component
public class SessionLogin {

    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextHolderStrategy securityContextHolder =
            SecurityContextHolder.getContextHolderStrategy();

    SessionLogin(SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository) {
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    public void start(UserId user, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(user.value()), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = securityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
    }
}
