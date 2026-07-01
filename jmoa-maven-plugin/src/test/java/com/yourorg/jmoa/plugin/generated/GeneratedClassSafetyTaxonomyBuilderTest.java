package com.yourorg.jmoa.plugin.generated;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedClassSafetyTaxonomyBuilderTest {

    @Test
    void keepsRuntimeProxyFamiliesUnsafeAndSpringAotRepackOnly() {
        GeneratedClassInventory inventory = new GeneratedClassInventory(
            "test",
            "now",
            2,
            2,
            256,
            List.of(),
            List.of(
                record("com.example.Service$$SpringCGLIB$$0", GeneratedClassFamily.SPRING_CGLIB),
                record("com.example.App__BeanDefinitions", GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS)
            )
        );

        GeneratedClassSafetyTaxonomy taxonomy = new GeneratedClassSafetyTaxonomyBuilder().build(inventory);

        GeneratedClassTransformEligibility proxy = taxonomy.eligibility().stream()
            .filter(item -> item.family() == GeneratedClassFamily.SPRING_CGLIB)
            .findFirst()
            .orElseThrow();
        assertEquals(GeneratedClassSafetyCategory.UNSAFE_RUNTIME_SEMANTIC, proxy.safetyCategory());
        assertTrue(proxy.forbiddenTransforms().contains("CONSOLIDATE"));

        GeneratedClassTransformEligibility aot = taxonomy.eligibility().stream()
            .filter(item -> item.family() == GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS)
            .findFirst()
            .orElseThrow();
        assertEquals(GeneratedClassSafetyCategory.SAFE_TO_REPACK_ONLY, aot.safetyCategory());
        assertTrue(aot.allowedTransforms().contains("REPACK_ONLY"));
    }

    private static GeneratedClassRecord record(String className, GeneratedClassFamily family) {
        return new GeneratedClassRecord(
            className,
            className.replace('.', '/'),
            "target/classes",
            className.replace('.', '/') + ".class",
            "application",
            family,
            List.of(),
            1,
            0,
            0,
            0,
            128,
            20,
            0,
            List.of(),
            GeneratedClassRiskLevel.UNKNOWN,
            null,
            true
        );
    }
}
