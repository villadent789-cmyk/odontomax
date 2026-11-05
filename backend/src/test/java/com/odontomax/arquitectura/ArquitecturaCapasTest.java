package com.odontomax.arquitectura;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.odontomax")
public class ArquitecturaCapasTest {

    private static final Set<String> MODULOS = Set.of(
        "shared", "identidad", "sedes", "agenda", "pacientes", "auditoria",
        "clinica", "tratamientos", "finanzas", "inventario", "reportes"
    );

    /**
     * Regla 1: domain no depende de org.springframework.., jakarta.persistence.., jakarta.validation..
     * Congelada porque el código actual tiene violaciones (ej. CitaColisionException importa Spring).
     */
    @ArchTest
    static final ArchRule domainNoDependeDeSpringJpaValidation =
        FreezingArchRule.freeze(
            noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.validation.."
                )
                .because("El dominio debe ser Java puro y no acoplarse a Spring, JPA ni Bean Validation")
                .allowEmptyShould(true)
        );

    /**
     * Regla 2a: domain no depende de application ni de infrastructure.
     * En el código actual no hay violaciones en esta dirección.
     */
    @ArchTest
    static final ArchRule domainNoDependeDeApplicationNiInfrastructure =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..application..",
                "..infrastructure.."
            )
            .because("El dominio no puede depender de capas exteriores (application o infrastructure)")
            .allowEmptyShould(true);

    /**
     * Regla 2b: application no depende de infrastructure.
     * Congelada porque el código actual en capas acopla application directamente con infrastructure.persistence.
     */
    @ArchTest
    static final ArchRule applicationNoDependeDeInfrastructure =
        FreezingArchRule.freeze(
            noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("La capa application no debe depender de infrastructure; debe invertir dependencias mediante puertos")
                .allowEmptyShould(true)
        );

    /**
     * Regla 3: las excepciones en ..domain.exception.. no llevan @ResponseStatus.
     * Congelada porque CitaColisionException lleva @ResponseStatus(HttpStatus.CONFLICT).
     */
    @ArchTest
    static final ArchRule excepcionesDeDominioNoLlevanResponseStatus =
        FreezingArchRule.freeze(
            noClasses()
                .that().resideInAPackage("..domain.exception..")
                .should().beAnnotatedWith("org.springframework.web.bind.annotation.ResponseStatus")
                .because("Las excepciones de dominio no deben llevar @ResponseStatus; el código HTTP lo decide GlobalExceptionHandler")
                .allowEmptyShould(true)
        );

    /**
     * Regla 4: las clases en ..infrastructure.web.. no dependen de repositorios
     * (..infrastructure.persistence.. ni clases que extiendan JpaRepository/Repository).
     * Apunta a los futuros controladores en la estructura hexagonal.
     */
    private static final DescribedPredicate<JavaClass> esRepositorioOClasePersistencia =
        new DescribedPredicate<>("clases en ..infrastructure.persistence.. o que extiendan Repository") {
            @Override
            public boolean test(JavaClass javaClass) {
                return javaClass.getPackageName().contains(".infrastructure.persistence")
                    || javaClass.isAssignableTo("org.springframework.data.repository.Repository");
            }
        };

    @ArchTest
    static final ArchRule webNoDependeDeRepositorios =
        noClasses()
            .that().resideInAPackage("..infrastructure.web..")
            .should().dependOnClassesThat(esRepositorioOClasePersistencia)
            .because("Los controladores web solo llaman a casos de uso (port/in), nunca a repositorios")
            .allowEmptyShould(true);

    /**
     * Regla 5: un módulo no accede a ..infrastructure.persistence.. de otro módulo.
     */
    private static String extraerModulo(String packageName) {
        if (packageName == null || !packageName.startsWith("com.odontomax.")) {
            return null;
        }
        String[] parts = packageName.split("\\.");
        if (parts.length >= 3 && MODULOS.contains(parts[2])) {
            return parts[2];
        }
        return null;
    }

    private static final ArchCondition<JavaClass> noAccederAPersistenciaDeOtroModulo =
        new ArchCondition<>("no acceder a infrastructure.persistence de otro módulo") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String moduloOrigen = extraerModulo(javaClass.getPackageName());
                if (moduloOrigen == null) {
                    return;
                }
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    String moduloDestino = extraerModulo(targetPackage);
                    if (moduloDestino != null && !moduloDestino.equals(moduloOrigen)
                            && targetPackage.contains(".infrastructure.persistence")) {
                        String message = String.format(
                            "Clase %s del módulo '%s' accede a la persistencia del módulo '%s': %s",
                            javaClass.getName(), moduloOrigen, moduloDestino, dependency.getDescription()
                        );
                        events.add(SimpleConditionEvent.violated(dependency, message));
                    }
                }
            }
        };

    @ArchTest
    static final ArchRule moduloNoAccedeAPersistenciaDeOtroModulo =
        classes()
            .that().resideInAPackage("com.odontomax..")
            .should(noAccederAPersistenciaDeOtroModulo)
            .because("Un módulo no toca las entidades ni los repositorios internos de otro módulo")
            .allowEmptyShould(true);
}
