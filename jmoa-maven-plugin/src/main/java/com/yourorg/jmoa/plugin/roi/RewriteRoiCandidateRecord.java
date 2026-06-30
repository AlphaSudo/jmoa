package com.yourorg.jmoa.plugin.roi;

import java.util.List;

/**
 * Per-site structured record for Phase 20 candidate inventory.
 *
 * <p>Contains all fields needed to classify, cost, and rank a framework lambda site
 * for admission scaling decisions. Built from existing {@code LambdaFilterDecision} data
 * without requiring new scanning.</p>
 */
public record RewriteRoiCandidateRecord(
    // --- Identity ---
    String siteKey,
    String ownerClass,
    String ownerPackage,
    String dependencyCoordinate,

    // --- Lambda shape ---
    String samInterface,
    String implOwner,
    String implMethod,
    String implDescriptor,
    int implTag,
    boolean capturing,
    boolean serializable,

    // --- Profile evidence ---
    boolean observedInProfile,
    long invocationCount,
    String profileHeat,          // "HOT" / "COLD" / "UNKNOWN"
    boolean missingProfile,

    // --- Source classification ---
    boolean frameworkSite,
    boolean applicationSite,
    String rootKind,             // ClassRootKind name

    // --- Current filter decision ---
    String currentDecision,      // "ELIGIBLE" or "EXCLUDED"
    String currentExclusionReason,
    String deniedReasonPrimary,
    List<String> deniedReasons,

    // --- Framework safety ---
    String frameworkSafetyLevel,
    List<String> frameworkSafetyReasons,

    // --- Access planning ---
    String accessVisibility,
    boolean accessPlannerApproved,
    String accessPlanKind,       // "TIER1_PUBLIC_LOOKUP" / "TIER2_PACKAGE_DIRECT" / null

    // --- Hypothetical tier (if admitted) ---
    String wouldBeTier,          // "TIER1" / "TIER2" / "UNKNOWN"
    String wouldBeTierConfidence, // "HIGH" / "MEDIUM" / "LOW"
    List<String> tierInferenceWarnings,

    // --- ROI classification ---
    RewriteRoiSafetyTier safetyTier,
    List<String> riskReasons,
    RewriteRoiAdmissionPrerequisite admissionPrerequisite,

    // --- Cost estimates ---
    String estimatedRisk,        // "LOW" / "MEDIUM" / "HIGH"
    String estimatedReplacementCost, // "LOW" / "MEDIUM" / "HIGH"
    long estimatedStandaloneCostBytes,
    long estimatedMarginalCostBytes
) {

    public RewriteRoiCandidateRecord {
        deniedReasons = deniedReasons == null ? List.of() : List.copyOf(deniedReasons);
        frameworkSafetyReasons = frameworkSafetyReasons == null ? List.of() : List.copyOf(frameworkSafetyReasons);
        tierInferenceWarnings = tierInferenceWarnings == null ? List.of() : List.copyOf(tierInferenceWarnings);
        riskReasons = riskReasons == null ? List.of() : List.copyOf(riskReasons);
    }
}
