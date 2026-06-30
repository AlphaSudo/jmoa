package com.yourorg.jmoa.plugin.roi;

import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetySummary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Main Phase 20 orchestrator. Ties together all sub-phases (20A–20G) and produces
 * a complete {@link RewriteRoiReport}.
 *
 * <p>Includes a hard reconciliation gate: if candidateInventory count does not
 * match frameworkSafetySummary.totalFrameworkSites, the report is flagged as
 * unreliable.</p>
 */
public final class RewriteRoiAnalyzer {

    private static final Logger LOGGER = Logger.getLogger(RewriteRoiAnalyzer.class.getName());

    private static final int CURRENT_PLANNED_SITES = 134;

    /**
     * Run all Phase 20 analysis sub-phases and produce the complete report.
     *
     * @param filterResult the complete filter result from Mode C scanning
     * @param profilePath the file path to the loaded profile
     * @param profileIndex the loaded profile index
     * @return the aggregate ROI report
     */
    public RewriteRoiReport analyze(LambdaFilterResult filterResult, java.io.File profilePath, com.yourorg.jmoa.plugin.filter.LambdaProfileIndex profileIndex) {
        // Compute profile provenance
        Map<String, Object> profileProvenance = new LinkedHashMap<>();
        boolean profileLoaded = profileIndex != null && !profileIndex.invocationCountsBySiteKey().isEmpty();
        profileProvenance.put("profileLoaded", profileLoaded);
        profileProvenance.put("profilePath", profilePath != null ? profilePath.getAbsolutePath() : "NONE");
        
        long profileSiteCount = profileLoaded ? profileIndex.invocationCountsBySiteKey().size() : 0;
        profileProvenance.put("profileSiteCount", profileSiteCount);
        
        long matchedProfileSiteCount = filterResult.decisions().stream()
            .filter(com.yourorg.jmoa.plugin.filter.LambdaFilterDecision::observedInProfile)
            .count();
        profileProvenance.put("matchedProfileSiteCount", matchedProfileSiteCount);
        
        long observedFrameworkSiteCount = filterResult.decisions().stream()
            .filter(com.yourorg.jmoa.plugin.filter.LambdaFilterDecision::observedInProfile)
            .filter(d -> d.frameworkSafetyDecision() != null && d.frameworkSafetyDecision().level() != com.yourorg.jmoa.plugin.framework.FrameworkSafetyLevel.APPLICATION)
            .count();
        profileProvenance.put("observedFrameworkSiteCount", observedFrameworkSiteCount);
        
        long unmatchedProfileSiteCount = profileSiteCount - matchedProfileSiteCount;
        if (unmatchedProfileSiteCount < 0) unmatchedProfileSiteCount = 0;
        profileProvenance.put("unmatchedProfileSiteCount", unmatchedProfileSiteCount);

        // Phase 20A + 20C: Classify all candidates
        RewriteRoiCandidateClassifier classifier = new RewriteRoiCandidateClassifier();
        List<RewriteRoiCandidateRecord> candidates = classifier.classify(filterResult);

        // Reconciliation gate (hard validation)
        Map<String, Object> reconciliation = reconcile(candidates, filterResult);

        // Phase 20B: Denial analysis
        RewriteRoiDenialAnalyzer denialAnalyzer = new RewriteRoiDenialAnalyzer();
        Map<String, Long> denialBreakdown = denialAnalyzer.computeDenialBreakdown(candidates);
        Map<String, Object> denialAggregations = denialAnalyzer.analyze(candidates);
        Map<String, Long> tierBByPrerequisite = denialAnalyzer.computeTierBByPrerequisite(candidates);
        Map<String, Long> safetyTierSummary = denialAnalyzer.computeSafetyTierSummary(candidates);

        // Phase 20D: Cost estimation
        RewriteRoiCostEstimator costEstimator = new RewriteRoiCostEstimator();
        Map<String, Object> costModel = costEstimator.buildCostModel(candidates);

        // Phase 20E: Savings projections
        RewriteRoiSavingsProjector savingsProjector = new RewriteRoiSavingsProjector();
        double tier1Fraction = computeTier1Fraction(candidates);
        List<Map<String, Object>> savingsProjections = savingsProjector.project(CURRENT_PLANNED_SITES, tier1Fraction);
        Map<String, Object> observedCalibration = savingsProjector.buildObservedCalibration();
        Map<String, Object> currentBaseline = savingsProjector.buildCurrentBaseline();

        // Phase 20F: Batch ranking
        RewriteRoiBatchRanker batchRanker = new RewriteRoiBatchRanker();
        List<Map<String, Object>> roiRankings = batchRanker.rank(candidates);

        // Phase 20G: Admission plans
        RewriteRoiAdmissionPlanBuilder planBuilder = new RewriteRoiAdmissionPlanBuilder();
        List<Map<String, Object>> admissionPlans = planBuilder.buildPlans(candidates, CURRENT_PLANNED_SITES);

        // Scale assessment
        RewriteRoiScaleAssessment scaleAssessment = computeScaleAssessment(candidates, safetyTierSummary);

        return new RewriteRoiReport(
            currentBaseline,
            observedCalibration,
            candidates,
            denialBreakdown,
            denialAggregations,
            tierBByPrerequisite,
            safetyTierSummary,
            costModel,
            savingsProjections,
            roiRankings,
            admissionPlans,
            scaleAssessment,
            profileProvenance,
            reconciliation,
            scaleAssessment.recommendedDecision()
        );
    }

    /**
     * Reconcile candidate inventory with framework safety summary.
     * This is the go/no-go gate for trusting ROI numbers.
     */
    private Map<String, Object> reconcile(List<RewriteRoiCandidateRecord> candidates,
                                           LambdaFilterResult filterResult) {
        FrameworkSafetySummary fss = filterResult.frameworkSafetySummary();

        int candidateCount = candidates.size();
        int frameworkTotal = fss.totalFrameworkSites();
        
        long candidateAllowedFramework = filterResult.decisions().stream()
            .filter(d -> d.frameworkSafetyDecision() != null && d.frameworkSafetyDecision().level() != com.yourorg.jmoa.plugin.framework.FrameworkSafetyLevel.APPLICATION)
            .filter(d -> d.frameworkSafetyDecision().allowed())
            .count();
            
        long candidateDeniedFramework = filterResult.decisions().stream()
            .filter(d -> d.frameworkSafetyDecision() != null && d.frameworkSafetyDecision().level() != com.yourorg.jmoa.plugin.framework.FrameworkSafetyLevel.APPLICATION)
            .filter(d -> !d.frameworkSafetyDecision().allowed())
            .count();

        int allowedFramework = fss.allowedFrameworkSites();
        int frameworkDenied = fss.deniedFrameworkSites();

        boolean inventoryMatch = candidateCount == frameworkTotal;
        boolean allowedMatch = candidateAllowedFramework == allowedFramework;
        boolean deniedMatch = candidateDeniedFramework == frameworkDenied;
        boolean allMatch = inventoryMatch && allowedMatch && deniedMatch;

        long executionEligibleSites = candidates.stream().filter(c -> "ELIGIBLE".equals(c.currentDecision())).count();
        long safetyAllowedButAccessDeniedSites = candidates.stream().filter(c -> "EXCLUDED".equals(c.currentDecision()) && "ACCESS_DENIED".equals(c.deniedReasonPrimary()) && c.safetyTier() != RewriteRoiSafetyTier.TIER_C && c.safetyTier() != RewriteRoiSafetyTier.TIER_D && c.admissionPrerequisite() == RewriteRoiAdmissionPrerequisite.ACCESS_FIX_REQUIRED).count();
        // Since we explicitly want safetyAllowedButAccessDenied, it should be the ones allowed by framework but blocked by access
        long safetyAllowedButBlockedByAccess = candidates.stream()
                .filter(c -> c.frameworkSafetyLevel() == null || c.frameworkSafetyLevel().equals("FRAMEWORK_SAFE"))
                .filter(c -> "EXCLUDED".equals(c.currentDecision()) && "ACCESS_DENIED".equals(c.deniedReasonPrimary()) || "ACCESS_UNKNOWN".equals(c.deniedReasonPrimary()) || c.admissionPrerequisite() == RewriteRoiAdmissionPrerequisite.ACCESS_FIX_REQUIRED && c.deniedReasons().contains("ACCESS_UNKNOWN"))
                .count();
        
        // Let's accurately calculate it: it's framework allowed minus execution eligible
        long safetyAllowedButAccessDenied = candidateAllowedFramework - executionEligibleSites;

        if (!allMatch) {
            LOGGER.warning("ROI reconciliation FAILED: "
                + "candidateCount=" + candidateCount + " vs frameworkTotal=" + frameworkTotal
                + ", candidateAllowed=" + candidateAllowedFramework + " vs allowedFramework=" + allowedFramework
                + ", candidateDenied=" + candidateDeniedFramework + " vs frameworkDenied=" + frameworkDenied);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidateCount", candidateCount);
        result.put("frameworkTotal", frameworkTotal);
        result.put("inventoryMatch", inventoryMatch);
        result.put("candidateAllowedFramework", candidateAllowedFramework);
        result.put("allowedFrameworkSites", allowedFramework);
        result.put("allowedMatch", allowedMatch);
        result.put("executionEligibleSites", executionEligibleSites);
        result.put("safetyAllowedButAccessDeniedSites", safetyAllowedButAccessDenied);
        result.put("candidateDeniedFramework", candidateDeniedFramework);
        result.put("frameworkDenied", frameworkDenied);
        result.put("deniedMatch", deniedMatch);
        result.put("reconciled", allMatch);
        return result;
    }

    private RewriteRoiScaleAssessment computeScaleAssessment(
        List<RewriteRoiCandidateRecord> candidates,
        Map<String, Long> safetyTierSummary
    ) {
        long tierS = safetyTierSummary.getOrDefault("TIER_S", 0L);
        long tierA = safetyTierSummary.getOrDefault("TIER_A", 0L);
        long tierBTotal = safetyTierSummary.getOrDefault("TIER_B", 0L);
        long tierC = safetyTierSummary.getOrDefault("TIER_C", 0L);
        long tierD = safetyTierSummary.getOrDefault("TIER_D", 0L);

        // Tier B breakdown
        long tierBProfile = candidates.stream()
            .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_B)
            .filter(c -> c.admissionPrerequisite() == RewriteRoiAdmissionPrerequisite.PROFILE_REQUIRED)
            .count();
        long tierBPackage = candidates.stream()
            .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_B)
            .filter(c -> c.admissionPrerequisite() == RewriteRoiAdmissionPrerequisite.PACKAGE_ALLOWLIST_REQUIRED)
            .count();
        long tierBSam = candidates.stream()
            .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_B)
            .filter(c -> c.admissionPrerequisite() == RewriteRoiAdmissionPrerequisite.SAM_SUPPORT_REQUIRED)
            .count();
        long tierBTier2Cost = candidates.stream()
            .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_B)
            .filter(c -> c.admissionPrerequisite() == RewriteRoiAdmissionPrerequisite.TIER2_COST_ACCEPTANCE_REQUIRED)
            .count();
        long tierBAccess = candidates.stream()
            .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_B)
            .filter(c -> c.admissionPrerequisite() == RewriteRoiAdmissionPrerequisite.ACCESS_FIX_REQUIRED)
            .count();

        // Maximum safe near-term rewrites = current + Tier A
        int maxSafeNearTerm = CURRENT_PLANNED_SITES + (int) tierA;

        // Can we reach these targets with Tier A + Tier B?
        long expandableA = tierA;
        long expandableAB = tierA + tierBTotal;
        boolean canReach300 = (CURRENT_PLANNED_SITES + expandableA) >= 300
            || (CURRENT_PLANNED_SITES + expandableAB) >= 300;
        boolean canReach500 = (CURRENT_PLANNED_SITES + expandableAB) >= 500;
        boolean canReach800 = (CURRENT_PLANNED_SITES + expandableAB) >= 800;

        // Determine limiting factor
        String limitingFactor;
        if (tierA + tierBTotal >= 50) {
            if (tierBProfile > tierA && tierBProfile > tierBPackage) {
                limitingFactor = "MISSING_PROFILE";
            } else if (tierBPackage > tierA) {
                limitingFactor = "UNKNOWN_PACKAGES";
            } else if (tierBTier2Cost > tierA) {
                limitingFactor = "TIER2_ADAPTER_COST";
            } else {
                limitingFactor = "SCALE_DEPENDENT";
            }
        } else if (tierBProfile >= 50) {
            limitingFactor = "MISSING_PROFILE"; // Many safe unobserved sites
        } else {
            limitingFactor = "INSUFFICIENT_CANDIDATES";
        }

        // Decision
        String decision;
        if (canReach300 && tierA >= 50) {
            decision = RewriteRoiScaleAssessment.DECISION_A_PROCEED;
        } else if (tierBTier2Cost > tierA + tierBProfile + tierBPackage) {
            decision = RewriteRoiScaleAssessment.DECISION_C_ADAPTER_CONSOLIDATION;
        } else if (tierBProfile > tierA && tierBProfile >= 50) {
            decision = RewriteRoiScaleAssessment.DECISION_D_PROFILE_NEEDED;
        } else {
            decision = RewriteRoiScaleAssessment.DECISION_B_NOT_ENOUGH;
        }

        return new RewriteRoiScaleAssessment(
            CURRENT_PLANNED_SITES,
            (int) tierS,
            (int) tierA,
            (int) tierBTotal,
            (int) tierBProfile,
            (int) tierBPackage,
            (int) tierBSam,
            (int) tierBTier2Cost,
            (int) tierBAccess,
            (int) tierC,
            (int) tierD,
            maxSafeNearTerm,
            canReach300,
            canReach500,
            canReach800,
            limitingFactor,
            decision
        );
    }

    private double computeTier1Fraction(List<RewriteRoiCandidateRecord> candidates) {
        long total = candidates.stream()
            .filter(c -> !"UNKNOWN".equals(c.wouldBeTier()))
            .count();
        if (total == 0) return 0.5;
        long tier1 = candidates.stream()
            .filter(c -> "TIER1".equals(c.wouldBeTier()))
            .count();
        return (double) tier1 / total;
    }
}
