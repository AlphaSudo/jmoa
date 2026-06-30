package com.yourorg.jmoa.plugin.roi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 20F: Groups candidates into logical batches and ranks by ROI.
 *
 * <p>Hard constraints:</p>
 * <ul>
 *   <li>Unknown package batches cannot be {@code ADMIT_NEXT} — must be {@code REVIEW_PACKAGE}</li>
 *   <li>Missing-profile batches cannot be {@code ADMIT_NEXT} — must be {@code PROFILE_FIRST}</li>
 *   <li>Unsupported SAM batches cannot be {@code ADMIT_NEXT} — must be {@code ADD_SAM_SUPPORT}</li>
 *   <li>Access-denied batches cannot be {@code ADMIT_NEXT} — must be {@code FIX_ACCESS}</li>
 * </ul>
 */
public final class RewriteRoiBatchRanker {

    private final RewriteRoiCostEstimator costEstimator = new RewriteRoiCostEstimator();

    /**
     * Group candidates into batches and rank them by ROI score.
     *
     * @param candidates classified candidate records
     * @return ranked list of batch summaries
     */
    public List<Map<String, Object>> rank(List<RewriteRoiCandidateRecord> candidates) {
        List<Map<String, Object>> batches = new ArrayList<>();

        batches.add(buildBatch("Tier1ObservedColdSafeBatch",
            "Tier 1, observed cold, safe package — lowest risk, lowest cost",
            candidates.stream()
                .filter(c -> "EXCLUDED".equals(c.currentDecision()))
                .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_A)
                .filter(c -> "TIER1".equals(c.wouldBeTier()))
                .filter(c -> c.observedInProfile())
                .toList()));

        batches.add(buildBatch("Tier1MissingProfileSafePackageBatch",
            "Tier 1, safe package, but missing profile observation",
            candidates.stream()
                .filter(c -> "EXCLUDED".equals(c.currentDecision()))
                .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_B)
                .filter(c -> "TIER1".equals(c.wouldBeTier()))
                .filter(c -> c.admissionPrerequisite() == RewriteRoiAdmissionPrerequisite.PROFILE_REQUIRED)
                .toList()));

        batches.add(buildBatch("Tier2ObservedColdSafeBatch",
            "Tier 2, observed cold, safe package — requires adapter but low risk",
            candidates.stream()
                .filter(c -> "EXCLUDED".equals(c.currentDecision()))
                .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_A || c.safetyTier() == RewriteRoiSafetyTier.TIER_B)
                .filter(c -> "TIER2".equals(c.wouldBeTier()))
                .filter(c -> c.observedInProfile())
                .toList()));

        batches.add(buildBatch("SpringBootOnlyBatch",
            "Sites in org.springframework.boot packages",
            candidates.stream()
                .filter(c -> "EXCLUDED".equals(c.currentDecision()))
                .filter(c -> c.ownerClass().startsWith("org/springframework/boot"))
                .filter(c -> c.safetyTier() != RewriteRoiSafetyTier.TIER_D)
                .toList()));

        batches.add(buildBatch("SpringCoreOnlyBatch",
            "Sites in org.springframework.core packages",
            candidates.stream()
                .filter(c -> "EXCLUDED".equals(c.currentDecision()))
                .filter(c -> c.ownerClass().startsWith("org/springframework/core"))
                .filter(c -> c.safetyTier() != RewriteRoiSafetyTier.TIER_D)
                .toList()));

        batches.add(buildBatch("SpringDataCommonsBatch",
            "Sites in org.springframework.data packages",
            candidates.stream()
                .filter(c -> "EXCLUDED".equals(c.currentDecision()))
                .filter(c -> c.ownerClass().startsWith("org/springframework/data"))
                .filter(c -> c.safetyTier() != RewriteRoiSafetyTier.TIER_D)
                .toList()));

        batches.add(buildBatch("UnknownPackageButObservedBatch",
            "Sites in unknown packages but observed in profile — requires package review",
            candidates.stream()
                .filter(c -> "EXCLUDED".equals(c.currentDecision()))
                .filter(c -> c.admissionPrerequisite() == RewriteRoiAdmissionPrerequisite.PACKAGE_ALLOWLIST_REQUIRED)
                .filter(c -> c.observedInProfile())
                .toList()));

        batches.add(buildBatch("CustomSamBatch",
            "Sites using unsupported SAM interfaces — requires SAM support addition",
            candidates.stream()
                .filter(c -> "EXCLUDED".equals(c.currentDecision()))
                .filter(c -> c.admissionPrerequisite() == RewriteRoiAdmissionPrerequisite.SAM_SUPPORT_REQUIRED)
                .toList()));

        // Remove empty batches and sort by ROI score (descending)
        batches.removeIf(batch -> (int) batch.get("candidateCount") == 0);
        batches.sort(Comparator.comparingInt((Map<String, Object> b) ->
            (int) b.get("roiScore")).reversed());

        return batches;
    }

    private Map<String, Object> buildBatch(String batchName, String description,
                                            List<RewriteRoiCandidateRecord> batchCandidates) {
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("batchName", batchName);
        batch.put("description", description);
        batch.put("candidateCount", batchCandidates.size());

        if (batchCandidates.isEmpty()) {
            batch.put("estimatedRisk", "N/A");
            batch.put("estimatedReplacementCost", "N/A");
            batch.put("expectedLambdaReduction", 0);
            batch.put("roiScore", 0);
            batch.put("recommendation", RewriteRoiBatchRecommendation.DEFER.name());
            return batch;
        }

        // Determine aggregate risk
        long highRiskCount = batchCandidates.stream().filter(c -> "HIGH".equals(c.estimatedRisk())).count();
        long mediumRiskCount = batchCandidates.stream().filter(c -> "MEDIUM".equals(c.estimatedRisk())).count();
        String aggregateRisk;
        if (highRiskCount > batchCandidates.size() / 2) {
            aggregateRisk = "HIGH";
        } else if (mediumRiskCount + highRiskCount > batchCandidates.size() / 2) {
            aggregateRisk = "MEDIUM";
        } else {
            aggregateRisk = "LOW";
        }
        batch.put("estimatedRisk", aggregateRisk);

        // Replacement cost
        long totalMarginalCost = batchCandidates.stream()
            .mapToLong(RewriteRoiCandidateRecord::estimatedMarginalCostBytes).sum();
        long totalStandaloneCost = batchCandidates.stream()
            .mapToLong(RewriteRoiCandidateRecord::estimatedStandaloneCostBytes).sum();
        batch.put("estimatedReplacementCost", aggregateRisk.equals("HIGH") ? "HIGH" : (totalStandaloneCost > 50000 ? "MEDIUM" : "LOW"));
        batch.put("estimatedTotalMarginalCostBytes", totalMarginalCost);
        batch.put("estimatedTotalStandaloneCostBytes", totalStandaloneCost);
        batch.put("expectedLambdaReduction", batchCandidates.size());

        // Prerequisite composition
        long notObservedCount = batchCandidates.stream().filter(c -> c.deniedReasons().contains("NOT_OBSERVED") || c.currentExclusionReason() != null && c.currentExclusionReason().equals("NOT_OBSERVED")).count();
        long missingProfileCount = batchCandidates.stream().filter(c -> c.admissionPrerequisite() == RewriteRoiAdmissionPrerequisite.PROFILE_REQUIRED).count();
        long accessDeniedCount = batchCandidates.stream().filter(c -> c.admissionPrerequisite() == RewriteRoiAdmissionPrerequisite.ACCESS_FIX_REQUIRED).count();
        long frameworkSafetyDeniedCount = batchCandidates.stream().filter(c -> c.deniedReasons().stream().anyMatch(r -> r.startsWith("SAFE") == false && !r.equals("MISSING_PROFILE") && !r.equals("NOT_OBSERVED") && !r.equals("UNSUPPORTED_SAM_KIND"))).count();
        long unsupportedSamCount = batchCandidates.stream().filter(c -> c.admissionPrerequisite() == RewriteRoiAdmissionPrerequisite.SAM_SUPPORT_REQUIRED).count();
        long tier1Count = batchCandidates.stream().filter(c -> "TIER1".equals(c.wouldBeTier())).count();
        long tier2Count = batchCandidates.stream().filter(c -> "TIER2".equals(c.wouldBeTier())).count();
        long tierUnknownCount = batchCandidates.stream().filter(c -> "UNKNOWN".equals(c.wouldBeTier())).count();

        Map<String, Object> composition = new LinkedHashMap<>();
        composition.put("notObservedCount", notObservedCount);
        composition.put("missingProfileCount", missingProfileCount);
        composition.put("accessDeniedCount", accessDeniedCount);
        composition.put("frameworkSafetyDeniedCount", frameworkSafetyDeniedCount);
        composition.put("unsupportedSamCount", unsupportedSamCount);
        composition.put("wouldBeTier1Count", tier1Count);
        composition.put("wouldBeTier2Count", tier2Count);
        composition.put("wouldBeTierUnknownCount", tierUnknownCount);
        batch.put("prerequisiteComposition", composition);

        // Savings estimates (using speculative scenarios)
        Map<String, Object> netSavings = new LinkedHashMap<>();
        double costKb = totalMarginalCost / 1024.0;
        netSavings.put("veryLowKb", Math.round((batchCandidates.size() * 0.25 - costKb) * 100.0) / 100.0);
        netSavings.put("lowKb", Math.round((batchCandidates.size() * 0.5 - costKb) * 100.0) / 100.0);
        netSavings.put("mediumKb", Math.round((batchCandidates.size() * 1.0 - costKb) * 100.0) / 100.0);
        netSavings.put("highKb", Math.round((batchCandidates.size() * 2.0 - costKb) * 100.0) / 100.0);
        batch.put("estimatedNetSavingsKb", netSavings);

        // ROI score
        int avgCostScore = 0;
        for (RewriteRoiCandidateRecord c : batchCandidates) {
            avgCostScore += costEstimator.computeCostScore(c);
        }
        avgCostScore = batchCandidates.isEmpty() ? 0 : avgCostScore / batchCandidates.size();
        int expectedSavingsScore = batchCandidates.size() * 10;
        int riskPenalty = "HIGH".equals(aggregateRisk) ? 50 : "MEDIUM".equals(aggregateRisk) ? 20 : 0;
        long profiledCount = batchCandidates.stream().filter(RewriteRoiCandidateRecord::observedInProfile).count();
        int profileBonus = (int) (profiledCount * 5);
        int roiScore = expectedSavingsScore - (avgCostScore * batchCandidates.size()) - riskPenalty + profileBonus;
        batch.put("roiScore", roiScore);

        // Recommendation (with hard constraints)
        batch.put("recommendation", determineRecommendation(batchName, batchCandidates, aggregateRisk, missingProfileCount, accessDeniedCount).name());

        return batch;
    }

    private RewriteRoiBatchRecommendation determineRecommendation(
        String batchName,
        List<RewriteRoiCandidateRecord> batchCandidates,
        String aggregateRisk,
        long missingProfileCount,
        long accessIssues
    ) {
        double threshold = batchCandidates.size() * 0.8;

        if (batchName.contains("UnknownPackage")) {
            return RewriteRoiBatchRecommendation.REVIEW_PACKAGE;
        }
        if (batchName.contains("CustomSam")) {
            return RewriteRoiBatchRecommendation.ADD_SAM_SUPPORT;
        }

        if (missingProfileCount >= threshold) {
            return RewriteRoiBatchRecommendation.PROFILE_FIRST;
        }

        if (accessIssues >= threshold) {
            return RewriteRoiBatchRecommendation.FIX_ACCESS;
        }

        if ("HIGH".equals(aggregateRisk)) {
            return RewriteRoiBatchRecommendation.DEFER;
        }

        long tierDCount = batchCandidates.stream()
            .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_D)
            .count();
        if (tierDCount > batchCandidates.size() / 2) {
            return RewriteRoiBatchRecommendation.REJECT;
        }

        if ("LOW".equals(aggregateRisk)) {
            return RewriteRoiBatchRecommendation.ADMIT_NEXT;
        }

        return RewriteRoiBatchRecommendation.DEFER;
    }
}
