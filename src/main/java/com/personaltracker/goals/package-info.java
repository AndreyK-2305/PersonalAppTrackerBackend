/**
 * Metas: metas de ahorro y, más adelante, metas financiadas (ADR-0018). Los abonos y pagos son
 * marcas dentro de la meta: el módulo no crea movimientos ni depende de {@code finance}. Usa la
 * calculadora financiera de {@code shared}.
 */
@ApplicationModule(displayName = "Goals", allowedDependencies = {"shared", "identity :: api"})
package com.personaltracker.goals;

import org.springframework.modulith.ApplicationModule;
