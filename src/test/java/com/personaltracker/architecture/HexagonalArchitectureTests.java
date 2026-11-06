package com.personaltracker.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.personaltracker.PersonalTrackerApplication;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reglas de capas dentro de cada módulo (ADR-0001 y ADR-0002). Spring Modulith verifica los
 * límites entre módulos; estas reglas verifican la arquitectura hexagonal interna.
 *
 * <p>Las reglas sobre capas que aún no tienen clases permiten estar vacías: aplican
 * automáticamente a medida que cada módulo crece.
 */
@AnalyzeClasses(packagesOf = PersonalTrackerApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTests {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "org.hibernate..", "tools.jackson..", "com.fasterxml.jackson..")
            .because("el dominio es Java puro (regla arquitectónica 1)");

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..", "..api..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationUsesOnlyAllowedSpringAnnotations = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat(resideInAPackage("org.springframework..")
                    .and(not(resideInAnyPackage(
                            "org.springframework.stereotype..", "org.springframework.transaction.annotation.."))))
            .orShould().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
            .because("la capa de aplicación solo usa @Service/@Component y @Transactional (ADR-0002)")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule moduleApiDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule webAdaptersDoNotUsePersistenceAdapters = noClasses()
            .that().resideInAPackage("..infrastructure.web..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.persistence..")
            .because("los controladores delegan en casos de uso (regla arquitectónica 3)");

    @ArchTest
    static final ArchRule jpaEntitiesLiveInPersistenceAdapters = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..infrastructure.persistence..")
            .because("las entidades JPA están separadas de las de dominio (regla arquitectónica 5)")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule restControllersLiveInWebAdapters = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..infrastructure.web..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noFloatingPointInDomainOrApplication = noFields()
            .that().areDeclaredInClassesThat().resideInAnyPackage("..domain..", "..application..", "..api..")
            .should().haveRawType(double.class)
            .orShould().haveRawType(float.class)
            .orShould().haveRawType(Double.class)
            .orShould().haveRawType(Float.class)
            .because("los montos y tasas usan BigDecimal (restricción 9)")
            .allowEmptyShould(true);
}
