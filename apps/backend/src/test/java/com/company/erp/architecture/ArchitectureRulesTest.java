package com.company.erp.architecture;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.company.erp", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {
    @ArchTest
    static final ArchRule topLevelModulesMustBeFreeOfCycles = slices()
        .matching("com.company.erp.(*)..")
        .should().beFreeOfCycles();
}
