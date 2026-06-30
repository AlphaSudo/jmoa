package com.yourorg.jmoa.plugin.roi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 20E: Projects savings at multiple scale points.
 *
 * <p>Uses four speculative hidden-lambda saving scenarios (0.25/0.5/1.0/2.0 KB)
 * and includes the observed Phase 19 calibration as context. All projections are
 * explicitly labeled as speculative.</p>
 *
 * <p>From Phase 19 observed data: 132 removed lambdas produced basically neutral
 * used metaspace (-11 KB). So even the 0.5 KB estimate may be optimistic.</p>
 */
public final class RewriteRoiSavingsProjector {

    // Hidden-lambda saving scenarios (KB per avoided lambda class)
    static final double SCENARIO_VERY_LOW_KB = 0.25;
    static final double SCENARIO_LOW_KB = 0.5;
    static final double SCENARIO_MEDIUM_KB = 1.0;
    static final double SCENARIO_HIGH_KB = 2.0;

    // Scale points to project
    private static final int[] SCALE_POINTS = {134, 300, 500, 800, 1000};

    // Tier-mix replacement cost assumptions (KB per rewrite)
    private static final double TIER1_HEAVY_COST_KB_PER_SITE = 0.35;
    private static final double TIER2_HEAVY_COST_KB_PER_SITE = 0.95;
    private static final double MIXED_COST_KB_PER_SITE = 0.55;

    /**
     * Produce savings projections at all standard scale points.
     *
     * @param currentRewriteCount current planned rewrite count (e.g. 134)
     * @param tier1Fraction fraction of candidates that are Tier 1 (0.0 to 1.0)
     * @return list of projection maps for inclusion in the report
     */
    public List<Map<String, Object>> project(int currentRewriteCount, double tier1Fraction) {
        List<Map<String, Object>> projections = new ArrayList<>();
        for (int scalePoint : SCALE_POINTS) {
            projections.add(projectAt(scalePoint, tier1Fraction));
        }
        return projections;
    }

    /**
     * Project savings at a specific rewrite count.
     */
    public Map<String, Object> projectAt(int rewriteCount, double tier1Fraction) {
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("rewriteCount", rewriteCount);
        projection.put("speculative", true);

        // Gross savings under each scenario
        double grossVeryLow = rewriteCount * SCENARIO_VERY_LOW_KB;
        double grossLow = rewriteCount * SCENARIO_LOW_KB;
        double grossMedium = rewriteCount * SCENARIO_MEDIUM_KB;
        double grossHigh = rewriteCount * SCENARIO_HIGH_KB;

        Map<String, Object> grossSavings = new LinkedHashMap<>();
        grossSavings.put("veryLowKb", round(grossVeryLow));
        grossSavings.put("lowKb", round(grossLow));
        grossSavings.put("mediumKb", round(grossMedium));
        grossSavings.put("highKb", round(grossHigh));
        projection.put("grossSavingsKb", grossSavings);

        // Replacement cost under each tier-mix assumption
        double tier1HeavyCost = rewriteCount * TIER1_HEAVY_COST_KB_PER_SITE;
        double tier2HeavyCost = rewriteCount * TIER2_HEAVY_COST_KB_PER_SITE;
        double mixedCost = rewriteCount * MIXED_COST_KB_PER_SITE;
        // Calibrated cost based on actual tier fraction
        double calibratedCostPerSite = tier1Fraction * TIER1_HEAVY_COST_KB_PER_SITE
            + (1.0 - tier1Fraction) * TIER2_HEAVY_COST_KB_PER_SITE;
        double calibratedCost = rewriteCount * calibratedCostPerSite;

        Map<String, Object> replacementCost = new LinkedHashMap<>();
        replacementCost.put("tier1HeavyKb", round(tier1HeavyCost));
        replacementCost.put("tier2HeavyKb", round(tier2HeavyCost));
        replacementCost.put("mixedKb", round(mixedCost));
        replacementCost.put("calibratedKb", round(calibratedCost));
        replacementCost.put("tier1Fraction", round(tier1Fraction));
        projection.put("estimatedReplacementCostKb", replacementCost);

        // Net savings (using calibrated cost)
        Map<String, Object> netSavings = new LinkedHashMap<>();
        netSavings.put("veryLowKb", round(grossVeryLow - calibratedCost));
        netSavings.put("lowKb", round(grossLow - calibratedCost));
        netSavings.put("mediumKb", round(grossMedium - calibratedCost));
        netSavings.put("highKb", round(grossHigh - calibratedCost));
        projection.put("netSavingsKb", netSavings);

        // Break-even analysis
        Map<String, Object> breakEven = new LinkedHashMap<>();
        breakEven.put("breaksEvenAtVeryLow", grossVeryLow > calibratedCost);
        breakEven.put("breaksEvenAtLow", grossLow > calibratedCost);
        breakEven.put("breaksEvenAtMedium", grossMedium > calibratedCost);
        breakEven.put("breaksEvenAtHigh", grossHigh > calibratedCost);
        projection.put("breakEven", breakEven);

        return projection;
    }

    /**
     * Build the observed calibration section for the report.
     */
    public Map<String, Object> buildObservedCalibration() {
        Map<String, Object> cal = new LinkedHashMap<>();
        cal.put("lambdasRemoved", 132);
        cal.put("directReplacementCostBytes", 67634);
        cal.put("usedMetaspaceDeltaKbAfterForcedGC", -11);
        cal.put("committedMetaspaceDeltaKb", 31);
        cal.put("tier1OnlyLambdasRemoved", 64);
        cal.put("tier1OnlyUsedMetaspaceDeltaKb", 16);
        cal.put("tier1OnlyCommittedMetaspaceDeltaKb", 27);
        cal.put("note", "Model is uncertain. All projections are speculative. "
            + "Observed 132 removed lambdas produced basically neutral metaspace delta (-11 KB used). "
            + "Even 0.5 KB/lambda may be optimistic at current scale.");
        return cal;
    }

    /**
     * Build the current baseline section for the report.
     */
    public Map<String, Object> buildCurrentBaseline() {
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("allowedSites", 149);
        baseline.put("eligibleSites", 134);
        baseline.put("plannedSites", 134);
        baseline.put("rewrittenClasses", 72);
        baseline.put("lambdaClassesBefore", 1485);
        baseline.put("lambdaClassesAfter", 1353);
        baseline.put("lambdaReduction", 132);
        baseline.put("frameworkLambdaClassesBefore", 1153);
        baseline.put("frameworkLambdaClassesAfter", 1021);
        baseline.put("metaspaceUsedDeltaKb", -11);
        baseline.put("metaspaceCommittedDeltaKb", 31);
        baseline.put("generatedRuntimeClassBytes", 9436);
        baseline.put("generatedPackageAdapterCount", 70);
        baseline.put("generatedPackageAdapterClassBytes", 43508);
        baseline.put("rewrittenClassByteDelta", 14690);
        baseline.put("constantPoolEntryDelta", 683);
        return baseline;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
