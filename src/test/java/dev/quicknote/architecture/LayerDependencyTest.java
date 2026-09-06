package dev.quicknote.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Constitution Principle III, enforced mechanically rather than by reviewer vigilance.
 *
 * <p>Layering that is only a folder-naming convention decays. These rules fail the build.
 */
class LayerDependencyTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importProduction() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.quicknote");
    }

    @Test
    @DisplayName("domain imports no ORM type")
    void domainHasNoOrm() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..notes.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "org.hibernate..", "io.quarkus.hibernate..");
        rule.check(classes);
    }

    @Test
    @DisplayName("domain imports no transport type")
    void domainHasNoTransport() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..notes.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.ws.rs..", "org.jboss.resteasy..", "io.vertx..");
        rule.check(classes);
    }

    @Test
    @DisplayName("domain imports no framework type")
    void domainHasNoFramework() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..notes.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("io.quarkus..", "io.smallrye..", "org.eclipse.microprofile..");
        rule.check(classes);
    }

    @Test
    @DisplayName("domain does not depend on infrastructure")
    void domainDoesNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..notes.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..notes.infrastructure..");
        rule.check(classes);
    }

    @Test
    @DisplayName("domain does not depend on the transport layer")
    void domainDoesNotDependOnApi() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..notes.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..notes.api..");
        rule.check(classes);
    }

    @Test
    @DisplayName("application does not depend on transport or persistence")
    void applicationDependsInwardOnly() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..notes.application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..notes.api..", "..notes.infrastructure.entity..", "jakarta.ws.rs..");
        rule.check(classes);
    }

    @Test
    @DisplayName("JPA entities never escape the infrastructure package")
    void entitiesStayInInfrastructure() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("..notes.infrastructure..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..notes.infrastructure.entity..");
        rule.check(classes);
    }

    @Test
    @DisplayName("HTTP types never leave the transport layer")
    void httpTypesStayInApi() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..notes.domain..", "..notes.application..", "..notes.infrastructure..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..notes.api.dto..");
        rule.check(classes);
    }
}
