package com.personaltracker.architecture;

import com.personaltracker.PersonalTrackerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Límites entre módulos (Spring Modulith): sin ciclos, sin acceso a paquetes internos de otros
 * módulos y solo dependencias declaradas en cada package-info.java.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(PersonalTrackerApplication.class);

    @Test
    void respectsModuleBoundaries() {
        modules.verify();
    }

    /** Genera diagramas y la documentación de módulos en target/spring-modulith-docs. */
    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
