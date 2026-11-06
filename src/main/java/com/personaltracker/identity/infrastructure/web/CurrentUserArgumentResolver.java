package com.personaltracker.identity.infrastructure.web;

import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.identity.infrastructure.security.AuthenticatedUser;
import com.personaltracker.shared.domain.UserId;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resuelve {@link CurrentUser} a partir del principal de la sesión; nunca de datos del cliente. */
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && UserId.class.equals(parameter.getParameterType());
    }

    @Override
    public UserId resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return new UserId(user.userId());
        }
        throw new AuthenticationCredentialsNotFoundException("No hay un usuario autenticado en la sesión");
    }
}
