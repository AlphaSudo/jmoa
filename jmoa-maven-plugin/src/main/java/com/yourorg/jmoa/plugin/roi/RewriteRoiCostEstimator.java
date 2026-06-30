package com.yourorg.jmoa.plugin.roi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 20D: Estimates replacement cost per candidate using Phase 19 calibration data.
 *
 * <p>Distinguishes marginal vs standalone cost:</p>
 * <ul>
 *   <li>Standalone cost: cost if this is the first/only rewrite in its owner class</li>
 *   <li>Marginal cost: cost if the owner class is already being rewritten</li>
 * </ul>
 *
 * <p>Cost is not linear per site — first rewrite in a class is high cost,
 * additional sites in the same class have lower marginal cost.</p>
 */
public final class RewriteRoiCostEstimator {

    // --- Calibration constants from Phase 19 measured data ---
    static final double AVG_ADAPTER_BYTES = 621.5;
    static final double AVG_REWRITTEN_CLASS_GROWTH_BYTES = 204.0;
    static final double AVG_CONSTANT_POOL_ENTRIES = 9.5;
    static final long RUNTIME_CLASS_BYTES = 9436;
    static final double AVG_CONSTANT_POOL_ENTRY_BYTES = 15.0; // approximate

    // --- Cost score weights ---
    private static final int TIER1_BASE_COST = 1;
    private static final int TIER2_BASE_COST = 3;
    private static final int NEW_ADAPTER_COST = 3;
    private static final int REUSE_ADAPTER_COST = 1;
    private static final int UNKNOWN_PACKAGE_PENALTY = 5;
    private static final int MISSING_PROFILE_PENALTY = 4;
    private static final int ACCESS_DENIED_PENALTY = 10;
    private static final int UNSUPPORTED_SAM_PENALTY = 8;

    /**
     * Compute cost score for a candidate.
     *
     * @param candidate the candidate record
     * @return integer cost score (lower is better)
     */
    public int computeCostScore(RewriteRoiCandidateRecord candidate) {
        int score = 0;

        // Tier cost
        score += "TIER1".equals(candidate.wouldBeTier()) ? TIER1_BASE_COST : TIER2_BASE_COST;

        // Adapter cost
        if ("TIER2".equals(candidate.wouldBeTier())) {
            score += NEW_ADAPTER_COST; // conservative: assume new adapter
        }

        // Risk penalties
        if (candidate.missingProfile()) {
            score += MISSING_PROFILE_PENALTY;
        }
        if ("FRAMEWORK_UNKNOWN".equals(candidate.frameworkSafetyLevel())) {
            score += UNKNOWN_PACKAGE_PENALTY;
        }
        if ("UNKNOWN".equals(candidate.accessVisibility())) {
            score += ACCESS_DENIED_PENALTY;
        }
        for (String reason : candidate.deniedReasons()) {
            if ("UNSUPPORTED_SAM_KIND".equals(reason)) {
                score += UNSUPPORTED_SAM_PENALTY;
                break;
            }
        }

        return score;
    }

    /**
     * Estimate standalone cost in bytes for rewriting a single new site in a class
     * that is not currently being rewritten.
     */
    public static long estimateStandaloneCostBytes(String wouldBeTier) {
        if ("TIER1".equals(wouldBeTier)) {
            // Class growth + constant pool growth (no adapter)
            return Math.round(AVG_REWRITTEN_CLASS_GROWTH_BYTES + (AVG_CONSTANT_POOL_ENTRIES * AVG_CONSTANT_POOL_ENTRY_BYTES));
        }
        if ("TIER2".equals(wouldBeTier)) {
            // Class growth + constant pool growth + adapter class
            return Math.round(AVG_REWRITTEN_CLASS_GROWTH_BYTES + (AVG_CONSTANT_POOL_ENTRIES * AVG_CONSTANT_POOL_ENTRY_BYTES) + AVG_ADAPTER_BYTES);
        }
        // Unknown — use Tier 2 as conservative estimate
        return Math.round(AVG_REWRITTEN_CLASS_GROWTH_BYTES + (AVG_CONSTANT_POOL_ENTRIES * AVG_CONSTANT_POOL_ENTRY_BYTES) + AVG_ADAPTER_BYTES);
    }

    /**
     * Estimate marginal cost in bytes for adding another rewrite to a class
     * that is already being rewritten.
     */
    public static long estimateMarginalCostBytes(String wouldBeTier) {
        // Marginal cost is lower: no full class growth overhead, just incremental constant pool
        double marginalCpEntries = AVG_CONSTANT_POOL_ENTRIES * 0.6; // reduced for shared base
        if ("TIER1".equals(wouldBeTier)) {
            return Math.round(marginalCpEntries * AVG_CONSTANT_POOL_ENTRY_BYTES);
        }
        if ("TIER2".equals(wouldBeTier)) {
            // Might need adapter but package adapter may already exist
            return Math.round(marginalCpEntries * AVG_CONSTANT_POOL_ENTRY_BYTES + (AVG_ADAPTER_BYTES * 0.3));
        }
        return Math.round(marginalCpEntries * AVG_CONSTANT_POOL_ENTRY_BYTES + (AVG_ADAPTER_BYTES * 0.3));
    }

    /**
     * Build the cost model summary for the report.
     */
    public Map<String, Object> buildCostModel(List<RewriteRoiCandidateRecord> candidates) {
        Map<String, Object> model = new LinkedHashMap<>();

        // Calibration data
        Map<String, Object> calibration = new LinkedHashMap<>();
        calibration.put("source", "Phase 19 measured data");
        calibration.put("avgAdapterBytes", AVG_ADAPTER_BYTES);
        calibration.put("avgRewrittenClassGrowthBytes", AVG_REWRITTEN_CLASS_GROWTH_BYTES);
        calibration.put("avgConstantPoolEntries", AVG_CONSTANT_POOL_ENTRIES);
        calibration.put("runtimeClassBytes", RUNTIME_CLASS_BYTES);
        model.put("calibration", calibration);

        // Score distribution
        long tier1LowCost = candidates.stream()
            .filter(c -> "TIER1".equals(c.wouldBeTier()))
            .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_A || c.safetyTier() == RewriteRoiSafetyTier.TIER_S)
            .count();
        long tier2MediumCost = candidates.stream()
            .filter(c -> "TIER2".equals(c.wouldBeTier()))
            .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_A || c.safetyTier() == RewriteRoiSafetyTier.TIER_B)
            .count();
        long highCost = candidates.stream()
            .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_C || c.safetyTier() == RewriteRoiSafetyTier.TIER_D)
            .count();

        Map<String, Object> distribution = new LinkedHashMap<>();
        distribution.put("tier1LowCostCandidates", tier1LowCost);
        distribution.put("tier2MediumCostCandidates", tier2MediumCost);
        distribution.put("highCostOrRiskyCandidates", highCost);
        model.put("costDistribution", distribution);

        // Aggregate estimates
        long totalStandaloneCostBytes = candidates.stream()
            .filter(c -> c.safetyTier() != RewriteRoiSafetyTier.TIER_D)
            .mapToLong(RewriteRoiCandidateRecord::estimatedStandaloneCostBytes)
            .sum();
        long totalMarginalCostBytes = candidates.stream()
            .filter(c -> c.safetyTier() != RewriteRoiSafetyTier.TIER_D)
            .mapToLong(RewriteRoiCandidateRecord::estimatedMarginalCostBytes)
            .sum();

        Map<String, Object> aggregates = new LinkedHashMap<>();
        aggregates.put("totalStandaloneCostBytes", totalStandaloneCostBytes);
        aggregates.put("totalMarginalCostBytes", totalMarginalCostBytes);
        aggregates.put("averageStandaloneCostBytesPerSite",
            candidates.isEmpty() ? 0 : totalStandaloneCostBytes / Math.max(1, candidates.stream()
                .filter(c -> c.safetyTier() != RewriteRoiSafetyTier.TIER_D).count()));
        model.put("aggregateEstimates", aggregates);

        return model;
    }
}
