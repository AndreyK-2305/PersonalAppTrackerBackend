/**
 * Usuarios, credenciales, sesión y autorización. Es dueño de la configuración de Spring Security.
 * En la Etapa 1 solo contiene la seguridad base (todo denegado salvo rutas públicas); el registro,
 * el inicio de sesión y el perfil llegan en la Etapa 2.
 */
@ApplicationModule(displayName = "Identity", allowedDependencies = "shared")
package com.personaltracker.identity;

import org.springframework.modulith.ApplicationModule;
