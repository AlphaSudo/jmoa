package com.yourorg.jmoa.plugin.dedup;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

class AccessPlannerTest {

    @Test
    void publicTargetsStayTier1() {
        DeduplicationKey key = key("com/example/PublicHolder", "length");

        AccessPlan plan = AccessPlanner.plan(key, AccessResolver.Visibility.PUBLIC, true).orElseThrow();

        assertEquals(AccessTier.TIER1_PUBLIC_LOOKUP, plan.tier());
        assertNull(plan.targetPackageInternal());
        assertFalse(plan.requiresAccessWidening());
    }

    @Test
    void packagePrivateTargetsMoveToTier2() {
        DeduplicationKey key = key("com/example/PackageHolder", "packageHelper");

        AccessPlan plan = AccessPlanner.plan(key, AccessResolver.Visibility.PACKAGE_PRIVATE, true).orElseThrow();

        assertEquals(AccessTier.TIER2_PACKAGE_DIRECT, plan.tier());
        assertEquals("com/example", plan.targetPackageInternal());
        assertFalse(plan.requiresAccessWidening());
    }

    @Test
    void privateSyntheticTargetsCanBeWidenedIntoTier2() {
        DeduplicationKey key = key("com/example/PrivateHolder", "lambda$run$0");

        AccessPlan plan = AccessPlanner.plan(key, AccessResolver.Visibility.PRIVATE, true).orElseThrow();

        assertEquals(AccessTier.TIER2_PACKAGE_DIRECT, plan.tier());
        assertTrue(plan.requiresAccessWidening());
    }

    @Test
    void privateNonSyntheticTargetsAreRejected() {
        DeduplicationKey key = key("com/example/PrivateHolder", "secretHelper");

        assertTrue(AccessPlanner.plan(key, AccessResolver.Visibility.PRIVATE, true).isEmpty());
    }

    private static DeduplicationKey key(String owner, String implName) {
        return new DeduplicationKey(
            "java/util/function/Function",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            Opcodes.H_INVOKESTATIC,
            owner,
            implName,
            "(Ljava/lang/String;)Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/String;"
        );
    }
}
