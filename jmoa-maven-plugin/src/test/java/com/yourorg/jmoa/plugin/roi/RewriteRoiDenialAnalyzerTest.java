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
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RewriteRoiDenialAnalyzerTest {

    private final RewriteRoiDenialAnalyzer analyzer = new RewriteRoiDenialAnalyzer();
    private final RewriteRoiCandidateClassifier classifier = new RewriteRoiCandidateClassifier();

    private LambdaMeta buildMeta(String owner, boolean capturing) {
        String siteKey = owner + "::m()V#0|apply|()Ljava/util/function/Function;|6|" + owner + "::lambda$m$0()V";
        return new LambdaMeta(siteKey, owner, owner.substring(0, owner.lastIndexOf('/')),
            "m", "()V", 0, "apply", "()Ljava/util/function/Function;", "()Ljava/lang/Object;",
            capturing, false, 6, owner, "lambda$m$0", "()V", "(Ljava/lang/Object;)Ljava/lang/Object;", 0);
    }

    @Test
    void tierBBreakdownByPrerequisite() {
        LambdaMeta m1 = buildMeta("org/springframework/boot/A", false);
        LambdaMeta m2 = buildMeta("com/example/custom/B", false);
        LambdaMeta m3 = buildMeta("org/springframework/boot/C", false);

        LambdaFilterDecision d1 = LambdaFilterDecision.excluded(m1, false, false, 0,
            ExclusionReason.NOT_OBSERVED, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(m1.siteKey(), m1.ownerInternalName(), ClassRootKind.EXPANDED_DEPENDENCY,
                FrameworkSafetyLevel.FRAMEWORK_SAFE, List.of(FrameworkSafetyReason.MISSING_PROFILE)));
        LambdaFilterDecision d2 = LambdaFilterDecision.excluded(m2, true, false, 200,
            ExclusionReason.FRAMEWORK_SAFETY_DENIED, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(m2.siteKey(), m2.ownerInternalName(), ClassRootKind.EXPANDED_DEPENDENCY,
                FrameworkSafetyLevel.FRAMEWORK_UNKNOWN, List.of(FrameworkSafetyReason.UNKNOWN_FRAMEWORK_PACKAGE)));
        LambdaFilterDecision d3 = LambdaFilterDecision.excluded(m3, false, false, 0,
            ExclusionReason.NOT_OBSERVED, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(m3.siteKey(), m3.ownerInternalName(), ClassRootKind.EXPANDED_DEPENDENCY,
                FrameworkSafetyLevel.FRAMEWORK_SAFE, List.of(FrameworkSafetyReason.MISSING_PROFILE)));

        List<RewriteRoiCandidateRecord> candidates = classifier.classify(new LambdaFilterResult(List.of(d1, d2, d3)));
        Map<String, Long> tierBPrereqs = analyzer.computeTierBByPrerequisite(candidates);

        // d1 and d3 are Tier B PROFILE_REQUIRED; d2 is now stricter Tier C PACKAGE_ALLOWLIST_REQUIRED.
        assertEquals(2L, tierBPrereqs.getOrDefault("PROFILE_REQUIRED", 0L));
        assertEquals(0L, tierBPrereqs.getOrDefault("PACKAGE_ALLOWLIST_REQUIRED", 0L));
    }

    @Test
    void denialBreakdownCountsMatchExcludedTotal() {
        LambdaMeta m1 = buildMeta("org/springframework/boot/X", false);
        LambdaMeta m2 = buildMeta("org/springframework/boot/Y", true);

        LambdaFilterDecision d1 = LambdaFilterDecision.excluded(m1, false, false, 0,
            ExclusionReason.NOT_OBSERVED, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(m1.siteKey(), m1.ownerInternalName(), ClassRootKind.EXPANDED_DEPENDENCY,
                FrameworkSafetyLevel.FRAMEWORK_SAFE, List.of(FrameworkSafetyReason.MISSING_PROFILE)));
        LambdaFilterDecision d2 = LambdaFilterDecision.excluded(m2, true, false, 100,
            ExclusionReason.CAPTURING, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(m2.siteKey(), m2.ownerInternalName(), ClassRootKind.EXPANDED_DEPENDENCY,
                FrameworkSafetyLevel.FRAMEWORK_SAFE, List.of(FrameworkSafetyReason.CAPTURING_LAMBDA)));

        List<RewriteRoiCandidateRecord> candidates = classifier.classify(new LambdaFilterResult(List.of(d1, d2)));
        Map<String, Long> breakdown = analyzer.computeDenialBreakdown(candidates);

        long totalDenied = breakdown.values().stream().mapToLong(Long::longValue).sum();
        assertEquals(2, totalDenied, "Denial breakdown total must match excluded count");
    }

    @Test
    void safetyTierSummaryCoversAllTiers() {
        LambdaMeta m1 = buildMeta("org/springframework/boot/Z", false);
        LambdaFilterDecision d1 = LambdaFilterDecision.excluded(m1, false, false, 0,
            ExclusionReason.NOT_OBSERVED, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(m1.siteKey(), m1.ownerInternalName(), ClassRootKind.EXPANDED_DEPENDENCY,
                FrameworkSafetyLevel.FRAMEWORK_SAFE, List.of(FrameworkSafetyReason.MISSING_PROFILE)));

        List<RewriteRoiCandidateRecord> candidates = classifier.classify(new LambdaFilterResult(List.of(d1)));
        Map<String, Long> summary = analyzer.computeSafetyTierSummary(candidates);

        // All tier keys should be present
        for (RewriteRoiSafetyTier tier : RewriteRoiSafetyTier.values()) {
            assertTrue(summary.containsKey(tier.name()), "Missing tier: " + tier.name());
        }
    }
}
