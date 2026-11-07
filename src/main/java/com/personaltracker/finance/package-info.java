/**
 * Dinero: productos (efectivo, cuentas, billeteras, tarjetas y "Otro"), categorías, movimientos y
 * dashboard financiero. Es la única fuente de verdad del dinero (ADR-0013); los demás módulos lo
 * usan solo a través de su paquete {@code api}.
 */
@ApplicationModule(displayName = "Finance", allowedDependencies = {"shared", "identity :: api"})
package com.personaltracker.finance;

import org.springframework.modulith.ApplicationModule;
