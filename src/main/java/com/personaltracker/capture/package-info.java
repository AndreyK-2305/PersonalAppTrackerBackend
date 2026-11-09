/**
 * Captura automática: recibe las notificaciones bancarias que envían las automatizaciones de Atajos
 * del iPhone, las analiza y registra los movimientos a través de {@code finance :: api}
 * (ADR-0016 y ADR-0017).
 */
@ApplicationModule(displayName = "Capture", allowedDependencies = "shared")
package com.personaltracker.capture;

import org.springframework.modulith.ApplicationModule;
