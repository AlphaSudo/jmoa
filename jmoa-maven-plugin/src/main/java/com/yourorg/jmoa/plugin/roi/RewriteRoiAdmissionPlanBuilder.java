package com.yourorg.jmoa.plugin.roi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 20G: Builds three admission expansion plans without implementing them.
 *
 * <p>The aggressive plan does NOT include Tier C — Tier C requires separate design review
 * and is never included in any automatic admission plan.</p>
 */
public final class RewriteRoiAdmissionPlanBuilder {

    /**
     * Build three admission plans: conservative, balanced, aggressive.
     *
     * @param candidates classified candidate records
     * @param currentPlannedSites current number of planned rewrite sites
     * @return list of plan summaries
     */
    public List<Map<String, Object>> buildPlans(List<RewriteRoiCandidateRecord> candidates,
                                                 int currentPlannedSites) {
        List<Map<String, Object>> plans = new ArrayList<>();
        plans.add(buildConservativePlan(candidates, currentPlannedSites));
        plans.add(buildBalancedPlan(candidates, currentPlannedSites));
        plans.add(buildAggressivePlan(candidates, currentPlannedSites));
        return plans;
    }

    private Map<String, Object> buildConservativePlan(List<RewriteRoiCandidateRecord> candidates,
                                                       int currentPlannedSites) {
        // Conservative: only Tier A, only observed, only Tier 1, only safe packages
        List<RewriteRoiCandidateRecord> eligible = candidates.stream()
            .filter(c -> "EXCLUDED".equals(c.currentDecision()))
            .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_A)
            .filter(c -> "TIER1".equals(c.wouldBeTier()))
            .filter(c -> c.observedInProfile())
            .toList();

        return buildPlan(
            "CONSERVATIVE",
            "Only Tier A candidates: observed, cold, non-capturing, supported SAM, "
                + "known safe package, Tier 1 only. Minimal risk.",
            eligible,
            currentPlannedSites,
            "LOW",
            List.of("TIER_A"),
            List.of(
                "Full correctness smoke test on patient-service",
                "Startup regression test",
                "Mode C optimized-jars classpath validation"
            ),
            List.of(
                "Any test failure rolls back to current 134-site baseline",
                "Metaspace committed delta > +100 KB triggers review",
                "Any Spring context failure is immediate rollback"
            )
        );
    }

    private Map<String, Object> buildBalancedPlan(List<RewriteRoiCandidateRecord> candidates,
                                                    int currentPlannedSites) {
        // Balanced: Tier A + selected Tier B (where prerequisite is achievable)
        List<RewriteRoiCandidateRecord> eligible = candidates.stream()
            .filter(c -> "EXCLUDED".equals(c.currentDecision()))
            .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_A
                || c.safetyTier() == RewriteRoiSafetyTier.TIER_B)
            .filter(c -> c.admissionPrerequisite() != RewriteRoiAdmissionPrerequisite.NEVER)
            .filter(c -> c.admissionPrerequisite() != RewriteRoiAdmissionPrerequisite.ACCESS_FIX_REQUIRED)
            .filter(c -> !"UNKNOWN".equals(c.wouldBeTier()))
            .toList();

        return buildPlan(
            "BALANCED",
            "Tier A + selected Tier B: observed unknown packages after review, "
                + "Tier 1 + low-cost Tier 2. Moderate risk.",
            eligible,
            currentPlannedSites,
            "MEDIUM",
            List.of("TIER_A", "TIER_B"),
            List.of(
                "Full correctness smoke test on patient-service",
                "Startup regression test",
                "Mode C optimized-jars classpath validation",
                "Per-package review of unknown package additions",
                "Forced-GC metaspace measurement at new scale"
            ),
            List.of(
                "Any test failure rolls back to previous baseline",
                "Metaspace committed delta > +200 KB triggers review",
                "Any Spring context failure is immediate rollback",
                "Any new package-level failure isolates that package"
            )
        );
    }

    private Map<String, Object> buildAggressivePlan(List<RewriteRoiCandidateRecord> candidates,
                                                      int currentPlannedSites) {
        // Aggressive: Tier A + all Tier B. NO Tier C — requires separate design review.
        List<RewriteRoiCandidateRecord> eligible = candidates.stream()
            .filter(c -> "EXCLUDED".equals(c.currentDecision()))
            .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_A
                || c.safetyTier() == RewriteRoiSafetyTier.TIER_B)
            .filter(c -> c.admissionPrerequisite() != RewriteRoiAdmissionPrerequisite.NEVER)
            .toList();

        return buildPlan(
            "AGGRESSIVE",
            "Tier A + all Tier B (all prerequisite groups). Broader Spring packages, "
                + "custom SAM support if safe. NO Tier C — requires separate design review.",
            eligible,
            currentPlannedSites,
            "MEDIUM_HIGH",
            List.of("TIER_A", "TIER_B"),
            List.of(
                "Full correctness smoke test on patient-service",
                "Startup regression test with timing threshold",
                "Mode C optimized-jars classpath validation",
                "Per-package review of all package additions",
                "Forced-GC metaspace measurement at new scale",
                "Custom SAM interface behavioral verification",
                "Access widening safety review for Tier 2 sites"
            ),
            List.of(
                "Any test failure rolls back to previous baseline",
                "Metaspace committed delta > +300 KB triggers review",
                "Any Spring context failure is immediate rollback",
                "Any new package-level failure isolates that package",
                "Startup regression > 100ms triggers review"
            )
        );
    }

    private Map<String, Object> buildPlan(
        String planName,
        String description,
        List<RewriteRoiCandidateRecord> eligibleCandidates,
        int currentPlannedSites,
        String riskLevel,
        List<String> includedSafetyTiers,
        List<String> requiredTests,
        List<String> rollbackConditions
    ) {
        int addedSites = eligibleCandidates.size();
        int totalRewrites = currentPlannedSites + addedSites;

        long tier1Count = eligibleCandidates.stream().filter(c -> "TIER1".equals(c.wouldBeTier())).count();
        long tier2Count = eligibleCandidates.stream().filter(c -> "TIER2".equals(c.wouldBeTier())).count();
        long unknownTierCount = eligibleCandidates.stream().filter(c -> "UNKNOWN".equals(c.wouldBeTier())).count();

        long totalStandaloneCost = eligibleCandidates.stream()
            .mapToLong(RewriteRoiCandidateRecord::estimatedStandaloneCostBytes).sum();
        long totalMarginalCost = eligibleCandidates.stream()
            .mapToLong(RewriteRoiCandidateRecord::estimatedMarginalCostBytes).sum();

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planName", planName);
        plan.put("description", description);
        plan.put("expectedAddedSites", addedSites);
        plan.put("expectedTotalRewrites", totalRewrites);
        plan.put("expectedLambdaReduction", addedSites);
        plan.put("expectedTier1Sites", tier1Count);
        plan.put("expectedTier2Sites", tier2Count);
        plan.put("expectedUnknownTierSites", unknownTierCount);
        plan.put("expectedAdapterCount", tier2Count); // approximate: one adapter per Tier 2 site
        plan.put("expectedRuntimeCostKb", Math.round(RewriteRoiCostEstimator.RUNTIME_CLASS_BYTES / 1024.0 * 100.0) / 100.0);
        plan.put("expectedReplacementCostKb", Math.round(totalMarginalCost / 1024.0 * 100.0) / 100.0);
        plan.put("expectedStandaloneCostKb", Math.round(totalStandaloneCost / 1024.0 * 100.0) / 100.0);
        plan.put("riskLevel", riskLevel);
        plan.put("includedSafetyTiers", includedSafetyTiers);
        plan.put("requiredTests", requiredTests);
        plan.put("rollbackConditions", rollbackConditions);

        return plan;
    }
}
