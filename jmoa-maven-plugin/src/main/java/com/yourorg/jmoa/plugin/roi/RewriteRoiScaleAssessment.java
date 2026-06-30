package com.yourorg.jmoa.plugin.roi;

/**
 * Phase 20 scale assessment — explicitly answers "can we reach X rewrites?"
 *
 * <p>This is the heart of Phase 20. It provides clear yes/no answers for
 * each scaling target along with the limiting factor.</p>
 */
public record RewriteRoiScaleAssessment(
    int currentPlannedSites,
    int tierSCount,
    int tierACandidateCount,
    int tierBTotalCount,
    int tierBProfileRequiredCount,
    int tierBPackageReviewCount,
    int tierBSamSupportCount,
    int tierBTier2CostCount,
    int tierBAccessFixCount,
    int tierCCount,
    int tierDCount,
    int maxSafeNearTermRewrites,
    boolean canReach300,
    boolean canReach500,
    boolean canReach800,
    String limitingFactor,
    String recommendedDecision
) {

    /**
     * Phase 20 exit decision codes:
     * <ul>
     *   <li>A — Enough safe profitable candidates exist → proceed to controlled admission expansion</li>
     *   <li>B — Not enough candidates exist → JMOA Mode C cannot reach memory target on this app yet</li>
     *   <li>C — Candidates exist but are mostly Tier 2 expensive → adapter consolidation required before scaling</li>
     *   <li>D — Candidates exist but profile evidence is missing → need better training workload</li>
     * </ul>
     */
    public static final String DECISION_A_PROCEED = "A";
    public static final String DECISION_B_NOT_ENOUGH = "B";
    public static final String DECISION_C_ADAPTER_CONSOLIDATION = "C";
    public static final String DECISION_D_PROFILE_NEEDED = "D";
}
