package com.yourorg.jmoa.plugin.roi;

import com.yourorg.jmoa.plugin.dedup.AccessPlan;
import com.yourorg.jmoa.plugin.dedup.AccessPlanner;
import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.dedup.AccessTier;
import com.yourorg.jmoa.plugin.dedup.DeduplicationKey;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyLevel;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Phase 20A + 20C: Classifies every framework lambda site into a {@link RewriteRoiCandidateRecord}
 * with safety tier assignment.
 *
 * <p>Built entirely from existing {@link LambdaFilterResult} data — no new scanning needed.</p>
 */
public final class RewriteRoiCandidateClassifier {

    /**
     * Classify all framework decisions from a filter result into candidate records.
     *
     * @param filterResult the complete filter result (must contain ALL sites, including early-denied)
     * @return list of candidate records for all framework sites
     */
    public List<RewriteRoiCandidateRecord> classify(LambdaFilterResult filterResult) {
        return filterResult.decisions().stream()
            .filter(this::isFrameworkSite)
            .map(this::toCandidate)
            .toList();
    }

    private boolean isFrameworkSite(LambdaFilterDecision decision) {
        if (decision.frameworkSafetyDecision() == null) {
            return false;
        }
        return decision.frameworkSafetyDecision().level() != FrameworkSafetyLevel.APPLICATION;
    }

    private RewriteRoiCandidateRecord toCandidate(LambdaFilterDecision decision) {
        var meta = decision.meta();
        var fsd = decision.frameworkSafetyDecision();

        // --- Profile heat classification ---
        String profileHeat;
        if (!decision.observedInProfile() && !decision.inferredCold()) {
            profileHeat = "UNKNOWN";
        } else if (decision.invocationCount() >= 10_000) {
            profileHeat = "HOT";
        } else {
            profileHeat = "COLD";
        }

        // --- Framework classification ---
        boolean frameworkSite = fsd.level() != FrameworkSafetyLevel.APPLICATION;
        boolean applicationSite = fsd.level() == FrameworkSafetyLevel.APPLICATION;
        boolean missingProfile = !decision.observedInProfile() && !decision.inferredCold();

        // --- Current decision ---
        String currentDecision = decision.eligible() ? "ELIGIBLE" : "EXCLUDED";
        String currentExclusionReason = decision.exclusionReason() == null ? null : decision.exclusionReason().name();

        // --- Denied reasons ---
        String deniedReasonPrimary = derivePrimaryDenialReason(decision);
        List<String> deniedReasons = deriveDenialReasons(decision);

        // --- Access planning ---
        String accessVisibility = decision.accessVisibility() == null ? "UNKNOWN" : decision.accessVisibility().name();
        boolean accessPlannerApproved = decision.eligible() && decision.tier() != null;
        String accessPlanKind = deriveAccessPlanKind(decision);

        // --- Hypothetical tier ---
        String wouldBeTier = deriveWouldBeTier(decision);
        String wouldBeTierConfidence = deriveWouldBeTierConfidence(decision, deniedReasons);
        List<String> tierInferenceWarnings = deriveTierInferenceWarnings(decision, wouldBeTier);

        // --- Safety tier ---
        List<String> riskReasons = new ArrayList<>();
        RewriteRoiSafetyTier safetyTier = classifySafetyTier(decision, riskReasons);
        RewriteRoiAdmissionPrerequisite prerequisite = classifyPrerequisite(decision, safetyTier);

        // --- Risk and cost ---
        String estimatedRisk = deriveEstimatedRisk(safetyTier);
        String estimatedReplacementCost = deriveEstimatedReplacementCost(wouldBeTier);
        long standaloneCost = RewriteRoiCostEstimator.estimateStandaloneCostBytes(wouldBeTier);
        long marginalCost = RewriteRoiCostEstimator.estimateMarginalCostBytes(wouldBeTier);

        return new RewriteRoiCandidateRecord(
            meta.siteKey(),
            meta.ownerInternalName(),
            meta.packageInternalName(),
            deriveDependencyCoordinate(fsd),
            meta.samInterfaceInternalName(),
            meta.implOwner(),
            meta.implName(),
            meta.implDesc(),
            meta.implTag(),
            meta.capturing(),
            meta.serializable(),
            decision.observedInProfile(),
            decision.invocationCount(),
            profileHeat,
            missingProfile,
            frameworkSite,
            applicationSite,
            fsd.rootKind() == null ? "UNKNOWN" : fsd.rootKind().name(),
            currentDecision,
            currentExclusionReason,
            deniedReasonPrimary,
            deniedReasons,
            fsd.level().name(),
            decision.frameworkSafetyReasons(),
            accessVisibility,
            accessPlannerApproved,
            accessPlanKind,
            wouldBeTier,
            wouldBeTierConfidence,
            tierInferenceWarnings,
            safetyTier,
            List.copyOf(riskReasons),
            prerequisite,
            estimatedRisk,
            estimatedReplacementCost,
            standaloneCost,
            marginalCost
        );
    }

    // --- Safety tier classification ---

    RewriteRoiSafetyTier classifySafetyTier(LambdaFilterDecision decision, List<String> riskReasons) {
        var fsd = decision.frameworkSafetyDecision();
        List<FrameworkSafetyReason> reasons = fsd.reasons();

        // Tier S: already allowed
        if (decision.eligible() && fsd.allowed()) {
            return RewriteRoiSafetyTier.TIER_S;
        }

        // Tier D: never admit
        if (decision.meta().capturing()) {
            riskReasons.add("CAPTURING_LAMBDA");
            return RewriteRoiSafetyTier.TIER_D;
        }
        if (decision.meta().serializable()) {
            riskReasons.add("SERIALIZABLE_LAMBDA");
            return RewriteRoiSafetyTier.TIER_D;
        }
        if (reasons.contains(FrameworkSafetyReason.PROXY_OR_ENHANCER_PACKAGE)) {
            riskReasons.add("PROXY_OR_ENHANCER_PACKAGE");
            return RewriteRoiSafetyTier.TIER_D;
        }
        if (reasons.contains(FrameworkSafetyReason.JACKSON_INTERNAL_PACKAGE)) {
            riskReasons.add("JACKSON_INTERNAL_PACKAGE");
            return RewriteRoiSafetyTier.TIER_D;
        }
        if (reasons.contains(FrameworkSafetyReason.HIBERNATE_INTERNAL_PACKAGE)) {
            riskReasons.add("HIBERNATE_INTERNAL_PACKAGE");
            return RewriteRoiSafetyTier.TIER_D;
        }
        if (reasons.contains(FrameworkSafetyReason.HOT_SITE)) {
            riskReasons.add("HOT_SITE");
            return RewriteRoiSafetyTier.TIER_D;
        }

        // Tier C: high risk
        if (reasons.contains(FrameworkSafetyReason.SPRING_REFLECTION_PACKAGE)) {
            riskReasons.add("SPRING_REFLECTION_PACKAGE");
            return RewriteRoiSafetyTier.TIER_C;
        }
        if (decision.accessVisibility() == AccessResolver.Visibility.PRIVATE
            && !isSyntheticLambdaTarget(decision)) {
            riskReasons.add("PRIVATE_NON_SYNTHETIC_ACCESS");
            return RewriteRoiSafetyTier.TIER_C;
        }
        
        boolean observed = decision.observedInProfile() || decision.inferredCold();
        
        // If it was observed and we still have UNKNOWN access, it's high risk.
        // If it wasn't observed, its access is null, so we don't automatically penalize it to Tier C.
        if (observed && decision.accessVisibility() == AccessResolver.Visibility.UNKNOWN) {
            riskReasons.add("ACCESS_UNKNOWN");
            return RewriteRoiSafetyTier.TIER_C;
        }
        if (fsd.level() == FrameworkSafetyLevel.FRAMEWORK_UNKNOWN) {
            // Unsafe / unknown package is Tier C, not B
            riskReasons.add("UNKNOWN_FRAMEWORK_PACKAGE");
            return RewriteRoiSafetyTier.TIER_C;
        }

        // Tier A: low risk — observed, cold, supported SAM, safe package, Tier 1 preferred
        boolean supportedSam = !reasons.contains(FrameworkSafetyReason.UNSUPPORTED_SAM_KIND);
        boolean safePackage = fsd.level() == FrameworkSafetyLevel.FRAMEWORK_SAFE;
        boolean wouldBeTier1 = "TIER1".equals(deriveWouldBeTier(decision));
        boolean notHot = !reasons.contains(FrameworkSafetyReason.HOT_SITE);

        if (observed && supportedSam && safePackage && notHot) {
            if (wouldBeTier1) {
                return RewriteRoiSafetyTier.TIER_A;
            }
            // Tier 2 but safe package — still relatively low risk
            riskReasons.add("TIER2_REQUIRED");
            return RewriteRoiSafetyTier.TIER_B;
        }

        // Tier B: medium risk — everything else that is not D or C (e.g. PROFILE_REQUIRED)
        if (!observed) {
            riskReasons.add("MISSING_PROFILE_OBSERVATION");
        }
        if (!supportedSam) {
            riskReasons.add("UNSUPPORTED_SAM_KIND");
        }
        if (!wouldBeTier1 && observed) {
            riskReasons.add("TIER2_REQUIRED");
        }
        if (decision.accessVisibility() == null || decision.accessVisibility() == AccessResolver.Visibility.UNKNOWN) {
            riskReasons.add("ACCESS_UNRESOLVED_DUE_TO_EARLY_DENIAL");
        }
        return RewriteRoiSafetyTier.TIER_B;
    }

    // --- Admission prerequisite classification ---

    private RewriteRoiAdmissionPrerequisite classifyPrerequisite(
        LambdaFilterDecision decision,
        RewriteRoiSafetyTier safetyTier
    ) {
        if (safetyTier == RewriteRoiSafetyTier.TIER_S) {
            return RewriteRoiAdmissionPrerequisite.NONE;
        }
        if (safetyTier == RewriteRoiSafetyTier.TIER_D) {
            return RewriteRoiAdmissionPrerequisite.NEVER;
        }

        var fsd = decision.frameworkSafetyDecision();
        List<FrameworkSafetyReason> reasons = fsd.reasons();

        // If it's missing a profile, that's the primary blocker (which also causes access to be unknown).
        if (reasons.contains(FrameworkSafetyReason.MISSING_PROFILE) || 
            (decision.exclusionReason() != null && decision.exclusionReason() == com.yourorg.jmoa.plugin.filter.ExclusionReason.NOT_OBSERVED)) {
            return RewriteRoiAdmissionPrerequisite.PROFILE_REQUIRED;
        }

        // Priority order for remaining restrictions
        if (decision.accessVisibility() == AccessResolver.Visibility.UNKNOWN) {
            return RewriteRoiAdmissionPrerequisite.ACCESS_FIX_REQUIRED;
        }
        if (reasons.contains(FrameworkSafetyReason.UNSUPPORTED_SAM_KIND)) {
            return RewriteRoiAdmissionPrerequisite.SAM_SUPPORT_REQUIRED;
        }
        if (reasons.contains(FrameworkSafetyReason.UNKNOWN_FRAMEWORK_PACKAGE)) {
            return RewriteRoiAdmissionPrerequisite.PACKAGE_ALLOWLIST_REQUIRED;
        }
        if (decision.exclusionReason() != null
            && decision.exclusionReason() == com.yourorg.jmoa.plugin.filter.ExclusionReason.FRAMEWORK_EXCLUDED) {
            return RewriteRoiAdmissionPrerequisite.FRAMEWORK_EXCLUSION_REMOVAL_REQUIRED;
        }

        // If the site would be Tier 2, that's a cost acceptance issue
        String wouldBeTier = deriveWouldBeTier(decision);
        if ("TIER2".equals(wouldBeTier)) {
            return RewriteRoiAdmissionPrerequisite.TIER2_COST_ACCEPTANCE_REQUIRED;
        }

        // Denied for some other reason but not permanently blocked
        return RewriteRoiAdmissionPrerequisite.PROFILE_REQUIRED;
    }

    // --- Hypothetical tier inference ---

    private String deriveWouldBeTier(LambdaFilterDecision decision) {
        if (decision.eligible() && decision.tier() != null) {
            return decision.tier().name();
        }

        // For denied sites, try to infer from access visibility
        AccessResolver.Visibility vis = decision.accessVisibility();
        var meta = decision.meta();
        
        if (vis == null || vis == AccessResolver.Visibility.UNKNOWN) {
            // Best-effort projection for unobserved sites
            if (meta.implName().startsWith("lambda$")) {
                vis = AccessResolver.Visibility.PRIVATE;
            } else {
                vis = AccessResolver.Visibility.PUBLIC;
            }
        }

        // Hypothetical access plan
        DeduplicationKey key = new DeduplicationKey(
            meta.samInterfaceInternalName(),
            meta.samMethodTypeDesc(),
            meta.implTag(),
            meta.implOwner(),
            meta.implName(),
            meta.implDesc(),
            meta.instantiatedMethodTypeDesc()
        );
        Optional<AccessPlan> plan = AccessPlanner.plan(key, vis, true);
        if (plan.isEmpty()) {
            return "UNKNOWN";
        }
        return plan.get().tier() == AccessTier.TIER1_PUBLIC_LOOKUP ? "TIER1" : "TIER2";
    }

    private String deriveWouldBeTierConfidence(LambdaFilterDecision decision, List<String> deniedReasons) {
        if (decision.eligible()) {
            return "HIGH";
        }
        if (deniedReasons.size() <= 1 && decision.accessVisibility() != null
            && decision.accessVisibility() != AccessResolver.Visibility.UNKNOWN) {
            return "HIGH";
        }
        if (decision.accessVisibility() == AccessResolver.Visibility.UNKNOWN) {
            return "LOW";
        }
        if (deniedReasons.size() > 2) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private List<String> deriveTierInferenceWarnings(LambdaFilterDecision decision, String wouldBeTier) {
        List<String> warnings = new ArrayList<>();
        if ("UNKNOWN".equals(wouldBeTier)) {
            warnings.add("Cannot determine tier — access visibility is unknown");
        }
        if (!decision.eligible() && !decision.observedInProfile()) {
            warnings.add("Site not observed in profile — tier inference may change with real profile data");
        }
        if (decision.accessVisibility() == AccessResolver.Visibility.UNKNOWN) {
            warnings.add("Access resolution failed — actual tier depends on runtime class loading");
        }
        return warnings;
    }

    // --- Helper methods ---

    private String derivePrimaryDenialReason(LambdaFilterDecision decision) {
        if (decision.eligible()) {
            return null;
        }
        if (decision.exclusionReason() != null) {
            return decision.exclusionReason().name();
        }
        var fsd = decision.frameworkSafetyDecision();
        if (fsd != null && !fsd.allowed()) {
            List<FrameworkSafetyReason> reasons = fsd.reasons();
            if (reasons.contains(FrameworkSafetyReason.MISSING_PROFILE)) return "MISSING_PROFILE";
            if (reasons.contains(FrameworkSafetyReason.UNSUPPORTED_SAM_KIND)) return "UNSUPPORTED_SAM_KIND";
            if (reasons.contains(FrameworkSafetyReason.HOT_SITE)) return "HOT_SITE";
            if (reasons.contains(FrameworkSafetyReason.CAPTURING_LAMBDA)) return "CAPTURING_LAMBDA";
            if (reasons.contains(FrameworkSafetyReason.SERIALIZABLE_LAMBDA)) return "SERIALIZABLE_LAMBDA";
            if (reasons.contains(FrameworkSafetyReason.UNKNOWN_FRAMEWORK_PACKAGE)) return "UNKNOWN_FRAMEWORK_PACKAGE";
            if (!reasons.isEmpty()) return reasons.get(reasons.size() - 1).name();
        }
        return "UNKNOWN";
    }

    private List<String> deriveDenialReasons(LambdaFilterDecision decision) {
        if (decision.eligible()) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (decision.exclusionReason() != null) {
            reasons.add(decision.exclusionReason().name());
        }
        var fsd = decision.frameworkSafetyDecision();
        if (fsd != null && !fsd.allowed()) {
            for (FrameworkSafetyReason r : fsd.reasons()) {
                String name = r.name();
                if (!reasons.contains(name)) {
                    reasons.add(name);
                }
            }
        }
        return reasons;
    }

    private String deriveAccessPlanKind(LambdaFilterDecision decision) {
        if (decision.eligible() && decision.tier() != null) {
            return decision.tier() == com.yourorg.jmoa.plugin.filter.LambdaTier.TIER1
                ? "TIER1_PUBLIC_LOOKUP"
                : "TIER2_PACKAGE_DIRECT";
        }
        return null;
    }

    private String deriveDependencyCoordinate(com.yourorg.jmoa.plugin.framework.FrameworkSafetyDecision fsd) {
        // Dependency coordinate is not currently tracked in the filter decision.
        // Use owner class as a proxy — the root kind indicates whether it's a dependency.
        if (fsd.rootKind() == com.yourorg.jmoa.plugin.ClassRootKind.EXPANDED_DEPENDENCY) {
            return "expanded-dependency:" + fsd.ownerClass();
        }
        if (fsd.rootKind() == com.yourorg.jmoa.plugin.ClassRootKind.ADDITIONAL_DIRECTORY) {
            return "additional-directory:" + fsd.ownerClass();
        }
        return "project-output";
    }

    private String deriveEstimatedRisk(RewriteRoiSafetyTier tier) {
        return switch (tier) {
            case TIER_S -> "LOW";
            case TIER_A -> "LOW";
            case TIER_B -> "MEDIUM";
            case TIER_C -> "HIGH";
            case TIER_D -> "HIGH";
        };
    }

    private String deriveEstimatedReplacementCost(String wouldBeTier) {
        return switch (wouldBeTier) {
            case "TIER1" -> "LOW";
            case "TIER2" -> "MEDIUM";
            default -> "HIGH";
        };
    }

    private boolean isSyntheticLambdaTarget(LambdaFilterDecision decision) {
        return decision.meta().implName().startsWith("lambda$");
    }
}
