package com.personaltracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.personaltracker.PersonalTrackerApplication;
import com.personaltracker.shared.domain.DomainException;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * Arquitectura hexagonal del módulo {@code goals} y de la calculadora financiera de {@code shared}
 * (ADR-0018). Complementa las reglas generales de {@link HexagonalArchitectureTests} con las que se
 * revisaron en la auditoría de la entrega 1 de Metas.
 */
@AnalyzeClasses(packagesOf = PersonalTrackerApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class GoalsModuleArchitectureTests {

    private static final String GOALS = "com.personaltracker.goals..";
    private static final String DOMAIN = "com.personaltracker.goals.domain..";
    private static final String APPLICATION = "com.personaltracker.goals.application..";
    private static final String INFRASTRUCTURE = "com.personaltracker.goals.infrastructure..";
    private static final String CALCULATOR = "com.personaltracker.shared.domain.finance..";

    // --- Dependencias entre capas -------------------------------------------------------------

    @ArchTest
    static final ArchRule theDomainOnlyKnowsItselfAndTheSharedKernel = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(DOMAIN, "com.personaltracker.shared.domain..", "java..");

    @ArchTest
    static final ArchRule theCalculatorIsPureJava = classes()
            .that().resideInAPackage(CALCULATOR)
            .should().onlyDependOnClassesThat().resideInAnyPackage("com.personaltracker.shared.domain..", "java..");

    @ArchTest
    static final ArchRule theApplicationOnlyKnowsTheDomainItsPortsAndSpringStereotypes = classes()
            .that().resideInAPackage(APPLICATION)
            .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN, APPLICATION,
                    "com.personaltracker.shared.domain..", "java..", "org.springframework.stereotype..",
                    "org.springframework.transaction.annotation..");

    @ArchTest
    static final ArchRule theModuleReachesOtherModulesOnlyThroughTheirApi = noClasses()
            .that().resideInAPackage(GOALS)
            .should().dependOnClassesThat().resideInAnyPackage("com.personaltracker.finance..",
                    "com.personaltracker.capture..", "com.personaltracker.identity.domain..",
                    "com.personaltracker.identity.application..", "com.personaltracker.identity.infrastructure..");

    @ArchTest
    static final ArchRule onlyTheIntegrationAdaptersTalkToOtherModules = noClasses()
            .that().resideInAPackage(GOALS).and().resideOutsideOfPackage("..infrastructure.integration..")
            .and().resideOutsideOfPackage("..infrastructure.web..")
            .should().dependOnClassesThat().resideInAPackage("com.personaltracker.identity..");

    @ArchTest
    static final ArchRule packagesAreFreeOfCycles =
            slices().matching("com.personaltracker.goals.(**)").should().beFreeOfCycles();

    // --- Puertos y adaptadores ----------------------------------------------------------------

    @ArchTest
    static final ArchRule outputPortsAreInterfaces = classes()
            .that().resideInAPackage("com.personaltracker.goals.application.port.out..")
            .should().beInterfaces();

    @ArchTest
    static final ArchRule everyOutputPortHasExactlyOneAdapter = classes()
            .that().resideInAPackage("com.personaltracker.goals.application.port.out..")
            .should(haveExactlyOneAdapterInInfrastructure());

    @ArchTest
    static final ArchRule adaptersAreNotPublic = classes()
            .that().resideInAPackage(INFRASTRUCTURE)
            .should().notBePublic()
            .because("los adaptadores se usan a través de sus puertos, no directamente");

    @ArchTest
    static final ArchRule webAdaptersDriveUseCasesNotPorts = noClasses()
            .that().resideInAPackage("com.personaltracker.goals.infrastructure.web..")
            .should().dependOnClassesThat().resideInAPackage("com.personaltracker.goals.application.port..");

    @ArchTest
    static final ArchRule drivenAdaptersDoNotCallUseCasesOrWeb = noClasses()
            .that().resideInAnyPackage("com.personaltracker.goals.infrastructure.persistence..",
                    "com.personaltracker.goals.infrastructure.integration..")
            .should().dependOnClassesThat().resideInAnyPackage("com.personaltracker.goals.application.usecase..",
                    "com.personaltracker.goals.infrastructure.web..");

    @ArchTest
    static final ArchRule adaptersOfDifferentKindsDoNotKnowEachOther = slices()
            .matching("com.personaltracker.goals.infrastructure.(*)..").should().notDependOnEachOther();

    // --- Casos de uso y dominio ---------------------------------------------------------------

    @ArchTest
    static final ArchRule everyPublicUseCaseMethodDeclaresItsTransaction = methods()
            .that().areDeclaredInClassesThat().resideInAPackage(APPLICATION)
            .and().areDeclaredInClassesThat().areAnnotatedWith(Service.class)
            .and().arePublic()
            .should().beAnnotatedWith(Transactional.class);

    /** H-06: lo único público del paquete son los casos de uso; sus colaboradores no salen de él. */
    @ArchTest
    static final ArchRule theOnlyPublicClassesOfTheUseCasePackageAreUseCases = classes()
            .that().resideInAPackage("com.personaltracker.goals.application.usecase..").and().arePublic()
            .should().beAnnotatedWith(Service.class)
            .because("un colaborador que no es un caso de uso no forma parte de la interfaz de la aplicación");

    @ArchTest
    static final ArchRule useCaseCollaboratorsStayInsideTheirPackage = classes()
            .that().resideInAPackage("com.personaltracker.goals.application.usecase..")
            .and().areNotAnnotatedWith(Service.class)
            .should().notBePublic()
            .andShould().onlyBeAccessed().byClassesThat()
            .resideInAPackage("com.personaltracker.goals.application.usecase..");

    @ArchTest
    static final ArchRule theDomainDoesNotReadTheClock = noClasses()
            .that().resideInAnyPackage(DOMAIN, CALCULATOR)
            .should().callMethod(LocalDate.class, "now")
            .orShould().callMethod(Instant.class, "now")
            .orShould().dependOnClassesThat().areAssignableTo(Clock.class)
            .because("la fecha de hoy entra como parámetro para que el dominio sea determinista");

    @ArchTest
    static final ArchRule domainExceptionsExtendTheSharedBase = classes()
            .that().resideInAPackage("com.personaltracker.goals.domain.exception..").and().areTopLevelClasses()
            .should().beAssignableTo(DomainException.class);

    @ArchTest
    static final ArchRule dependenciesAreInjectedThroughFinalFields = fields()
            .that().areDeclaredInClassesThat().resideInAPackage(GOALS)
            .and().areDeclaredInClassesThat(annotatedWithAStereotype())
            .should().beFinal();

    @ArchTest
    static final ArchRule nothingUsesFieldInjection = noFields()
            .that().areDeclaredInClassesThat().resideInAPackage(GOALS)
            .should().beAnnotatedWith(Autowired.class);

    // --- Cohesión del adaptador web -----------------------------------------------------------

    /** H-07: los DTO de la web no dependen de su controlador; el tipo de plan es un enum del dominio. */
    @ArchTest
    static final ArchRule webDtosDoNotDependOnTheirController = noClasses()
            .that().resideInAPackage("com.personaltracker.goals.infrastructure.web..")
            .and().areTopLevelClasses().and().areNotAnnotatedWith(RestController.class)
            .should().dependOnClassesThat(belongToAController());

    private static ArchCondition<JavaClass> haveExactlyOneAdapterInInfrastructure() {
        return new ArchCondition<>("tener exactamente un adaptador en infrastructure") {
            @Override
            public void check(JavaClass port, ConditionEvents events) {
                List<String> adapters = port.getAllSubclasses().stream()
                        .filter(candidate -> !candidate.isInterface())
                        .filter(candidate -> candidate.getPackageName().contains(".goals.infrastructure."))
                        .map(JavaClass::getSimpleName)
                        .toList();
                events.add(new SimpleConditionEvent(port, adapters.size() == 1,
                        port.getSimpleName() + " tiene " + adapters.size() + " adaptadores: " + adapters));
            }
        };
    }

    private static DescribedPredicate<JavaClass> annotatedWithAStereotype() {
        return DescribedPredicate.describe("anotadas con @Service, @Component o @RestController",
                javaClass -> javaClass.isAnnotatedWith(Service.class) || javaClass.isAnnotatedWith(Component.class)
                        || javaClass.isAnnotatedWith(RestController.class));
    }

    private static DescribedPredicate<JavaClass> belongToAController() {
        return DescribedPredicate.describe("pertenecen a un controlador",
                javaClass -> javaClass.isAnnotatedWith(RestController.class) || javaClass.getEnclosingClass()
                        .map(enclosing -> enclosing.isAnnotatedWith(RestController.class))
                        .orElse(false));
    }
}
