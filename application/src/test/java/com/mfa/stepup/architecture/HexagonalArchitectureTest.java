package com.mfa.stepup.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "com.mfa.stepup",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class HexagonalArchitectureTest {

    @ArchTest
    public static final ArchRule domain_must_not_depend_on_application =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule domain_must_not_depend_on_infrastructure =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule domain_must_not_depend_on_frameworks =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "jakarta.servlet.."
            )
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule application_must_not_depend_on_infrastructure =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true);
}
