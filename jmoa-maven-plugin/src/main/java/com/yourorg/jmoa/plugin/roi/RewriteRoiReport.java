package com.yourorg.jmoa.plugin.roi;

import java.util.List;
import java.util.Map;

/**
 * Aggregate data record holding all Phase 20 ROI analysis results.
 *
 * <p>Consumed by {@link RewriteRoiReportWriter} to produce both
 * {@code target/jmoa-rewrite-roi-report.json} and {@code docs/phase20-rewrite-roi.md}.</p>
 */
public record RewriteRoiReport(
    Map<String, Object> currentBaseline,
    Map<String, Object> observedCalibration,
    List<RewriteRoiCandidateRecord> candidateInventory,
    Map<String, Long> denialBreakdown,
    Map<String, Object> denialAggregations,
    Map<String, Long> tierBByPrerequisite,
    Map<String, Long> safetyTierSummary,
    Map<String, Object> costModel,
    List<Map<String, Object>> savingsProjections,
    List<Map<String, Object>> roiRankings,
    List<Map<String, Object>> recommendedAdmissionPlans,
    RewriteRoiScaleAssessment scaleAssessment,
    Map<String, Object> profileProvenance,
    Map<String, Object> reconciliation,
    String recommendedDecision
) {

    public RewriteRoiReport {
        candidateInventory = candidateInventory == null ? List.of() : List.copyOf(candidateInventory);
        savingsProjections = savingsProjections == null ? List.of() : List.copyOf(savingsProjections);
        roiRankings = roiRankings == null ? List.of() : List.copyOf(roiRankings);
        recommendedAdmissionPlans = recommendedAdmissionPlans == null ? List.of() : List.copyOf(recommendedAdmissionPlans);
    }
}
