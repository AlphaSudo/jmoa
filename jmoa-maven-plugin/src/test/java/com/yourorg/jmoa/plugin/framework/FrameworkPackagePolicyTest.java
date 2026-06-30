package com.yourorg.jmoa.plugin.framework;

import com.yourorg.jmoa.plugin.ClassRootKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameworkPackagePolicyTest {

    @Test
    void projectOutputIsApplication() {
        FrameworkPackagePolicy.PackageDecision decision = new FrameworkPackagePolicy(FrameworkSafetyConfig.defaults())
            .classifyOwner("com/example/AppService", ClassRootKind.PROJECT_OUTPUT);

        assertEquals(FrameworkSafetyLevel.APPLICATION, decision.level());
        assertTrue(decision.allowed());
    }

    @Test
    void safeSpringExpandedDependencyIsAllowed() {
        FrameworkPackagePolicy.PackageDecision decision = new FrameworkPackagePolicy(FrameworkSafetyConfig.defaults())
            .classifyOwner("org/springframework/context/support/AbstractApplicationContext", ClassRootKind.EXPANDED_DEPENDENCY);

        assertEquals(FrameworkSafetyLevel.FRAMEWORK_SAFE, decision.level());
        assertTrue(decision.allowed());
    }

    @Test
    void jacksonExpandedDependencyIsDenied() {
        FrameworkPackagePolicy.PackageDecision decision = new FrameworkPackagePolicy(FrameworkSafetyConfig.defaults())
            .classifyOwner("com/fasterxml/jackson/databind/ObjectMapper", ClassRootKind.EXPANDED_DEPENDENCY);

        assertEquals(FrameworkSafetyLevel.FRAMEWORK_UNSAFE, decision.level());
        assertFalse(decision.allowed());
    }

    @Test
    void unknownExpandedDependencyIsDeniedByDefault() {
        FrameworkPackagePolicy.PackageDecision decision = new FrameworkPackagePolicy(FrameworkSafetyConfig.defaults())
            .classifyOwner("org/acme/framework/GeneratedThing", ClassRootKind.EXPANDED_DEPENDENCY);

        assertEquals(FrameworkSafetyLevel.FRAMEWORK_UNKNOWN, decision.level());
        assertFalse(decision.allowed());
    }
}
