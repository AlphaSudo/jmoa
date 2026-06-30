package com.yourorg.jmoa.plugin.roi;

import com.yourorg.jmoa.plugin.ClassRootKind;
import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.filter.ExclusionReason;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;
import com.yourorg.jmoa.plugin.filter.LambdaTier;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyDecision;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyLevel;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyReason;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetySummary;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 20 go/no-go gate: the ROI report candidate inventory must reconcile
 * exactly with the existing framework safety summary.
 */
class RewriteRoiReportConsistencyTest {

    private final RewriteRoiAnalyzer analyzer = new RewriteRoiAnalyzer();

    private LambdaMeta buildMeta(String owner, boolean capturing, boolean serializable) {
        String siteKey = owner + "::m()V#0|apply|()Ljava/util/function/Function;|6|" + owner + "::lambda$m$0()V";
        return new LambdaMeta(siteKey, owner, owner.substring(0, owner.lastIndexOf('/')),
            "m", "()V", 0, "apply", "()Ljava/util/function/Function;", "()Ljava/lang/Object;",
            capturing, serializable, 6, owner, "lambda$m$0", "()V", "(Ljava/lang/Object;)Ljava/lang/Object;", 0);
    }

    @Test
    void reconciliationPassesWhenInventoryMatchesFrameworkSummary() {
        // Build a small universe: 1 allowed + 2 denied framework sites + 1 application site
        LambdaMeta allowed = buildMeta("org/springframework/boot/A", false, false);
        LambdaMeta denied1 = buildMeta("org/springframework/boot/B", false, false);
        LambdaMeta denied2 = buildMeta("org/springframework/cglib/proxy/C", false, false);
        LambdaMeta appSite = buildMeta("com/pro/service/App", false, false);

        LambdaFilterDecision d1 = LambdaFilterDecision.eligible(
            allowed, true, false, 500, LambdaTier.TIER1, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.allow(allowed.siteKey(), allowed.ownerInternalName(),
                ClassRootKind.EXPANDED_DEPENDENCY, FrameworkSafetyLevel.FRAMEWORK_SAFE,
                List.of(FrameworkSafetyReason.SAFE_EXPANDED_DEPENDENCY_PREFIX, FrameworkSafetyReason.COLD_PROFILED_SITE, FrameworkSafetyReason.SAFE_SAM_KIND)));

        LambdaFilterDecision d2 = LambdaFilterDecision.excluded(
            denied1, false, false, 0, ExclusionReason.NOT_OBSERVED, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(denied1.siteKey(), denied1.ownerInternalName(),
                ClassRootKind.EXPANDED_DEPENDENCY, FrameworkSafetyLevel.FRAMEWORK_SAFE,
                List.of(FrameworkSafetyReason.MISSING_PROFILE)));

        LambdaFilterDecision d3 = LambdaFilterDecision.excluded(
            denied2, false, false, 0, ExclusionReason.FRAMEWORK_SAFETY_DENIED, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(denied2.siteKey(), denied2.ownerInternalName(),
                ClassRootKind.EXPANDED_DEPENDENCY, FrameworkSafetyLevel.FRAMEWORK_UNKNOWN,
                List.of(FrameworkSafetyReason.PROXY_OR_ENHANCER_PACKAGE)));

        LambdaFilterDecision d4 = LambdaFilterDecision.eligible(
            appSite, true, false, 100, LambdaTier.TIER1, AccessResolver.Visibility.PUBLIC);

        LambdaFilterResult filterResult = new LambdaFilterResult(List.of(d1, d2, d3, d4));

        RewriteRoiReport report = analyzer.analyze(filterResult, null, null);

        // Reconciliation must pass
        Map<String, Object> recon = report.reconciliation();
        assertTrue((Boolean) recon.get("reconciled"),
            "Reconciliation must PASS: candidateCount=" + recon.get("candidateCount")
            + " frameworkTotal=" + recon.get("frameworkTotal")
            + " tierSCount=" + recon.get("tierSCount")
            + " allowedFramework=" + recon.get("allowedFrameworkSites"));

        // Candidate inventory should have 3 entries (3 framework, not the app site)
        assertEquals(3, report.candidateInventory().size());

        // Denial breakdown should be non-empty
        assertFalse(report.denialBreakdown().isEmpty());

        // Safety tier summary must include all tiers
        for (RewriteRoiSafetyTier tier : RewriteRoiSafetyTier.values()) {
            assertTrue(report.safetyTierSummary().containsKey(tier.name()));
        }

        // Scale assessment must be present
        assertNotNull(report.scaleAssessment());
        assertNotNull(report.recommendedDecision());
    }

    @Test
    void savingsProjectionsAllMarkedSpeculative() {
        LambdaMeta m = buildMeta("org/springframework/boot/X", false, false);
        LambdaFilterDecision d = LambdaFilterDecision.excluded(m, false, false, 0,
            ExclusionReason.NOT_OBSERVED, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(m.siteKey(), m.ownerInternalName(),
                ClassRootKind.EXPANDED_DEPENDENCY, FrameworkSafetyLevel.FRAMEWORK_SAFE,
                List.of(FrameworkSafetyReason.MISSING_PROFILE)));

        RewriteRoiReport report = analyzer.analyze(new LambdaFilterResult(List.of(d)), null, null);

        for (Map<String, Object> proj : report.savingsProjections()) {
            assertTrue((Boolean) proj.get("speculative"),
                "Every savings projection must be labeled speculative");
        }
    }

    @Test
    void tierCExcludedFromAdmissionPlans() {
        LambdaMeta m = buildMeta("org/springframework/beans/factory/Foo", false, false);
        LambdaFilterDecision d = LambdaFilterDecision.excluded(m, true, false, 500,
            ExclusionReason.FRAMEWORK_SAFETY_DENIED, AccessResolver.Visibility.UNKNOWN,
            FrameworkSafetyDecision.deny(m.siteKey(), m.ownerInternalName(),
                ClassRootKind.EXPANDED_DEPENDENCY, FrameworkSafetyLevel.FRAMEWORK_SAFE,
                List.of(FrameworkSafetyReason.SPRING_REFLECTION_PACKAGE)));

        RewriteRoiReport report = analyzer.analyze(new LambdaFilterResult(List.of(d)), null, null);

        // Verify the site is classified as Tier C
        assertEquals(1, report.candidateInventory().size());
        assertEquals(RewriteRoiSafetyTier.TIER_C, report.candidateInventory().get(0).safetyTier());

        // Tier C should NOT appear in any admission plan's included tiers
        for (Map<String, Object> plan : report.recommendedAdmissionPlans()) {
            @SuppressWarnings("unchecked")
            List<String> tiers = (List<String>) plan.get("includedSafetyTiers");
            assertFalse(tiers.contains("TIER_C"),
                "Tier C must NOT be included in " + plan.get("planName") + " admission plan");
        }
    }

    @Test
    void unknownPackageNeverGetsAdmitNextRecommendation() {
        LambdaMeta m = buildMeta("com/unknown/lib/Service", false, false);
        LambdaFilterDecision d = LambdaFilterDecision.excluded(m, true, false, 200,
            ExclusionReason.FRAMEWORK_SAFETY_DENIED, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(m.siteKey(), m.ownerInternalName(),
                ClassRootKind.EXPANDED_DEPENDENCY, FrameworkSafetyLevel.FRAMEWORK_UNKNOWN,
                List.of(FrameworkSafetyReason.UNKNOWN_FRAMEWORK_PACKAGE)));

        RewriteRoiReport report = analyzer.analyze(new LambdaFilterResult(List.of(d)), null, null);

        for (Map<String, Object> batch : report.roiRankings()) {
            int count = (int) batch.get("candidateCount");
            if (count > 0) {
                assertNotEquals("ADMIT_NEXT", batch.get("recommendation"),
                    "Unknown package batch must never get ADMIT_NEXT: " + batch.get("batchName"));
            }
        }
    }
}
