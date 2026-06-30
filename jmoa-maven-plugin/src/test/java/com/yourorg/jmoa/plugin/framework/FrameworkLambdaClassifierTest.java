package com.yourorg.jmoa.plugin.framework;

import com.yourorg.jmoa.plugin.ClassRootKind;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameworkLambdaClassifierTest {

    private static final FrameworkSafetyConfig ENABLED_CONFIG = new FrameworkSafetyConfig(
        true,
        true,
        true,
        false,
        FrameworkSafetyConfig.defaults().allowPrefixes(),
        FrameworkSafetyConfig.defaults().denyPrefixes(),
        10_000,
        FrameworkSafetyConfig.defaults().safeSamInterfaces(),
        Set.of()
    );

    @Test
    void allowsObservedColdSafeSpringSite() {
        FrameworkSafetyDecision decision = new FrameworkLambdaClassifier(ENABLED_CONFIG)
            .classify(meta("org/springframework/context/App", false, false, "()Ljava/util/function/Supplier;"), ClassRootKind.EXPANDED_DEPENDENCY, 3L, true);

        assertTrue(decision.allowed());
    }

    @Test
    void deniesHotSafeSpringSite() {
        FrameworkSafetyDecision decision = new FrameworkLambdaClassifier(ENABLED_CONFIG)
            .classify(meta("org/springframework/context/App", false, false, "()Ljava/util/function/Supplier;"), ClassRootKind.EXPANDED_DEPENDENCY, 20_000L, true);

        assertFalse(decision.allowed());
    }

    @Test
    void deniesUnobservedExpandedFrameworkSite() {
        FrameworkSafetyDecision decision = new FrameworkLambdaClassifier(ENABLED_CONFIG)
            .classify(meta("org/springframework/context/App", false, false, "()Ljava/util/function/Supplier;"), ClassRootKind.EXPANDED_DEPENDENCY, 0L, false);

        assertFalse(decision.allowed());
    }

    @Test
    void deniesUnsupportedSamKind() {
        FrameworkSafetyDecision decision = new FrameworkLambdaClassifier(ENABLED_CONFIG)
            .classify(meta("org/springframework/context/App", false, false, "()Ljava/util/concurrent/Callable;"), ClassRootKind.EXPANDED_DEPENDENCY, 1L, true);

        assertFalse(decision.allowed());
    }

    @Test
    void observedSiteKeyOverridesUnknownFrameworkPackageWhenStructuralGatesPass() {
        LambdaMeta meta = meta("org/springframework/security/web/firewall/StrictHttpFirewall", false, false, "()Ljava/util/function/Supplier;");
        FrameworkSafetyDecision decision = new FrameworkLambdaClassifier(configWithObservedSite(meta.siteKey()))
            .classify(meta, ClassRootKind.EXPANDED_DEPENDENCY, 1L, true);

        assertTrue(decision.allowed());
        assertTrue(decision.reasons().contains(FrameworkSafetyReason.UNKNOWN_FRAMEWORK_PACKAGE));
        assertTrue(decision.reasons().contains(FrameworkSafetyReason.OBSERVED_SITE_KEY_ADMITTED));
    }

    @Test
    void observedSiteKeyOverridesSpringReflectionPackageWhenStructuralGatesPass() {
        LambdaMeta meta = meta("org/springframework/beans/factory/support/DefaultSingletonBeanRegistry", false, false, "()Ljava/util/function/Supplier;");
        FrameworkSafetyDecision decision = new FrameworkLambdaClassifier(configWithObservedSite(meta.siteKey()))
            .classify(meta, ClassRootKind.EXPANDED_DEPENDENCY, 1L, true);

        assertTrue(decision.allowed());
        assertTrue(decision.reasons().contains(FrameworkSafetyReason.SPRING_REFLECTION_PACKAGE));
        assertTrue(decision.reasons().contains(FrameworkSafetyReason.OBSERVED_SITE_KEY_ADMITTED));
    }

    @Test
    void structuralGatesStillDenyObservedSiteKeyAdmissions() {
        LambdaMeta capturing = meta("org/springframework/security/web/firewall/StrictHttpFirewall", true, false, "()Ljava/util/function/Supplier;");
        LambdaMeta serializable = meta("org/springframework/security/web/firewall/StrictHttpFirewall", false, true, "()Ljava/util/function/Supplier;");
        LambdaMeta hot = meta("org/springframework/security/web/firewall/StrictHttpFirewall", false, false, "()Ljava/util/function/Supplier;");
        LambdaMeta unsupportedSam = meta("org/springframework/security/web/firewall/StrictHttpFirewall", false, false, "()Ljava/util/concurrent/Callable;");

        assertFalse(new FrameworkLambdaClassifier(configWithObservedSite(capturing.siteKey()))
            .classify(capturing, ClassRootKind.EXPANDED_DEPENDENCY, 1L, true)
            .allowed());
        assertFalse(new FrameworkLambdaClassifier(configWithObservedSite(serializable.siteKey()))
            .classify(serializable, ClassRootKind.EXPANDED_DEPENDENCY, 1L, true)
            .allowed());
        assertFalse(new FrameworkLambdaClassifier(configWithObservedSite(unsupportedSam.siteKey()))
            .classify(unsupportedSam, ClassRootKind.EXPANDED_DEPENDENCY, 1L, true)
            .allowed());
        assertFalse(new FrameworkLambdaClassifier(configWithObservedSite(hot.siteKey()))
            .classify(hot, ClassRootKind.EXPANDED_DEPENDENCY, 20_000L, true)
            .allowed());
    }

    private static FrameworkSafetyConfig configWithObservedSite(String siteKey) {
        return new FrameworkSafetyConfig(
            true,
            true,
            true,
            false,
            FrameworkSafetyConfig.defaults().allowPrefixes(),
            FrameworkSafetyConfig.defaults().denyPrefixes(),
            10_000,
            FrameworkSafetyConfig.defaults().safeSamInterfaces(),
            Set.of(siteKey)
        );
    }

    private static LambdaMeta meta(String owner, boolean capturing, boolean serializable, String indyFactoryDesc) {
        return new LambdaMeta(
            owner + "::method()V#0|apply|" + indyFactoryDesc + "|6|java/lang/String::trim()Ljava/lang/String;",
            owner,
            owner.substring(0, owner.lastIndexOf('/')),
            "method",
            "()V",
            0,
            "apply",
            indyFactoryDesc,
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            capturing,
            serializable,
            Opcodes.H_INVOKEVIRTUAL,
            "java/lang/String",
            "trim",
            "()Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/Object;",
            0L
        );
    }
}
