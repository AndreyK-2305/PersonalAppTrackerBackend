/**
 * Contrato público del módulo finance para el resto de módulos. Otros módulos solo pueden usar los
 * tipos de este paquete; declaran la dependencia como {@code "finance :: api"}. No expone tipos del
 * dominio de finance.
 */
@NamedInterface("api")
package com.personaltracker.finance.api;

import org.springframework.modulith.NamedInterface;
