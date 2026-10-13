package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaTier;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Tier2StaticModeAuditTest {

    @Test
    void auditsEveryModeFromOneDecisionSetAndProjectsPhysicalRoots() {
        LambdaFilterDecision objectRoot0 = site(
                "root0/Object#0", "apply", "()Ljava/util/function/Function;",
                "(Ljava/lang/Object;)Ljava/lang/Object;", 0);
        LambdaFilterDecision objectRoot1 = site(
                "root1/Object#0", "apply", "()Ljava/util/function/Function;",
                "(Ljava/lang/Object;)Ljava/lang/Object;", 1);
        LambdaFilterDecision intRoot0 = site(
                "root0/Int#0", "applyAsInt", "()Ljava/util/function/IntUnaryOperator;", "(I)I", 2);
        List<LambdaFilterDecision> decisions = List.of(objectRoot0, objectRoot1, intRoot0);
        Map<String, Integer> ordinals = Map.of(
                objectRoot0.meta().siteKey(), 0,
                objectRoot1.meta().siteKey(), 1,
                intRoot0.meta().siteKey(), 0);

        Tier2StaticModeAudit.AuditReport report = Tier2StaticModeAudit.build(
                decisions, ordinals, Map.of(0, "root-a", 1, "root-b"), Opcodes.V22);

        assertEquals("jmoa-tier2-static-mode-audit-v1", report.schemaVersion());
        assertEquals(7, report.modes().size());
        assertEquals(3, mode(report, "CURRENT", false).logicalAdapters());
        assertEquals(1, mode(report, "PACKAGE", false).logicalAdapters());
        assertEquals(2, mode(report, "PACKAGE_SAM", true).logicalAdapters());
        assertEquals(2, mode(report, "PACKAGE_SIGNATURE", true).logicalAdapters());
        Tier2StaticModeAudit.ModeAudit rootAware = mode(report, "ROOT_PACKAGE_SIGNATURE", true);
        assertEquals(3, rootAware.logicalAdapters());
        assertEquals(3, rootAware.physicalEmittedClasses());
        assertEquals(0, rootAware.multiRootAdapters());
        assertEquals(3, rootAware.adapters().stream().mapToInt(adapter -> adapter.siteKeys().size()).sum());
        assertTrue(rootAware.adapters().stream().allMatch(adapter -> adapter.siteKeys().size() == 1));
        assertTrue(report.modes().stream().allMatch(Tier2StaticModeAudit.ModeAudit::collisionFree));
        assertTrue(report.modes().stream().allMatch(Tier2StaticModeAudit.ModeAudit::generatedReferenceClosure));
        assertTrue(report.modes().stream().allMatch(Tier2StaticModeAudit.ModeAudit::byteDeterministic));
    }

    private static Tier2StaticModeAudit.ModeAudit mode(
            Tier2StaticModeAudit.AuditReport report, String mode, boolean compact) {
        return report.modes().stream()
                .filter(value -> value.mode().equals(mode) && value.compact() == compact)
                .findFirst().orElseThrow();
    }

    private static LambdaFilterDecision site(
            String siteKey, String indyName, String factoryDesc, String samDesc, int ordinal) {
        String pkg = "com/example";
        LambdaMeta meta = new LambdaMeta(
                siteKey, pkg + "/Owner", pkg, "run", "()V", ordinal,
                indyName, factoryDesc, samDesc, false, false, Opcodes.H_INVOKESTATIC,
                pkg + "/Helper", false, "helper", samDesc, samDesc, 1L);
        return LambdaFilterDecision.eligible(
                meta, true, false, 1L, LambdaTier.TIER2, AccessResolver.Visibility.PACKAGE_PRIVATE);
    }
}
