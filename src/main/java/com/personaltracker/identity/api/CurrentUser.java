package com.personaltracker.identity.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inyecta en un parámetro de controlador el {@code UserId} del usuario autenticado, obtenido de la
 * sesión. Es la única forma válida de conocer al propietario de un recurso (ADR-0003):
 *
 * <pre>{@code
 * @GetMapping
 * List<AccountResponse> list(@CurrentUser UserId owner) { ... }
 * }</pre>
 *
 * <p>Si no hay sesión, la petición termina en 401.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {}
