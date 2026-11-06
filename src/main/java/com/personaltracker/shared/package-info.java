/**
 * Núcleo compartido mínimo: tipos de dominio comunes y componentes transversales de infraestructura
 * (manejo de errores, web, OpenAPI). Es un módulo abierto: todos los módulos pueden usar sus tipos.
 * Solo se agrega aquí lo que realmente comparten varios módulos.
 */
@ApplicationModule(displayName = "Shared", type = ApplicationModule.Type.OPEN)
package com.personaltracker.shared;

import org.springframework.modulith.ApplicationModule;
