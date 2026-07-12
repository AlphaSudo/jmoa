package com.yourorg.jmoa.plugin.recommendation;

import java.util.List;

public final class RecommendationModels {

    private RecommendationModels() {
    }

    public enum AdmissionDecision {
        RECOMMEND_CONFIRMED,
        RECOMMEND_SCREEN_REQUIRED,
        APPLICATION_LOW_ROI_ARTIFACT_ONLY,
        APPLICATION_SCREEN_REQUIRED,
        GENERATED_REPORT_ONLY,
        GENERATED_MUTATION_BLOCKED,
        CANDIDATE_FOR_PROTOTYPE,
        ALLOW_ARTIFACT_ONLY,
        BLOCK_UNSAFE,
        BLOCK_SEMANTIC_FAILURE,
        BLOCK_NO_EVIDENCE,
        BLOCK_RUNTIME_PROMOTION,
        DIAGNOSTIC_ONLY,
        UNKNOWN
    }

    public enum ConfirmationScope {
        PUBLIC,
        PRIVATE,
        INTERNAL,
        UNKNOWN
    }

    public record RecommendationContext(
        String service,
        String launchMode,
        String runtimePolicy,
        ConfirmationScope confirmationScope
    ) {
        public RecommendationContext {
            confirmationScope = confirmationScope == null ? ConfirmationScope.UNKNOWN : confirmationScope;
        }
    }

    public record ReducerAdmissionInput(
        String metadataVersion,
        String service,
        String launchMode,
        String runtimePolicy,
        boolean v2bAttributeReportPresent,
        long v2bDebugAttributeBytes,
        long v2bLocalVariableTableBytes,
        long artifactBytesRemoved,
        int classesReduced,
        boolean applicationClassEvidencePresent,
        long applicationBytesRemoved,
        int applicationClassesReduced,
        boolean generatedSurfaceEvidencePresent,
        long generatedEstimatedBytes,
        int generatedClassCount,
        boolean generatedRuntimeRelevant,
        boolean generatedUnsafeFamilyPresent,
        boolean generatedPrototypeAdmissionEvidencePresent,
        boolean artifactEvidencePresent,
        boolean rawAuditPresent,
        int failedAudits,
        int signedJarsSkipped,
        int multiReleaseJarsSkipped,
        int sealedJarsSkipped,
        Boolean semanticSmokePassed,
        String screenVerdict,
        boolean hasV2CConfirmation,
        String v2cVerdict,
        boolean hasV2DAttribution,
        boolean diagnosticOnly,
        String reducerEngine,
        List<String> strippedAttributes,
        List<String> unsafeAttributesRequested,
        String confirmedService,
        String confirmedLaunchMode,
        String confirmedRuntimePolicy,
        ConfirmationScope confirmationScope,
        List<String> sourceReports
    ) {
        public ReducerAdmissionInput {
            metadataVersion = valueOr(metadataVersion, "v2m-reducer-admission-input");
            service = valueOr(service, "unknown");
            launchMode = valueOr(launchMode, "UNKNOWN");
            runtimePolicy = valueOr(runtimePolicy, "UNKNOWN");
            screenVerdict = valueOr(screenVerdict, "NOT_RUN");
            v2cVerdict = valueOr(v2cVerdict, "NO_CLAIM");
            reducerEngine = valueOr(reducerEngine, "unknown");
            strippedAttributes = strippedAttributes == null ? List.of() : List.copyOf(strippedAttributes);
            unsafeAttributesRequested = unsafeAttributesRequested == null
                ? List.of()
                : List.copyOf(unsafeAttributesRequested);
            confirmationScope = confirmationScope == null ? ConfirmationScope.UNKNOWN : confirmationScope;
            sourceReports = sourceReports == null ? List.of() : List.copyOf(sourceReports);
        }

        private static String valueOr(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }
    }

    public record ReducerRecommendation(
        String metadataVersion,
        String generatedAt,
        AdmissionDecision decision,
        ConfirmationScope confirmationScope,
        boolean protocolMatchesConfirmedScope,
        boolean runtimePromotionAllowed,
        List<String> reasons,
        List<String> evidenceGaps,
        List<String> nextActions,
        ReducerAdmissionInput input,
        List<String> boundaries
    ) {
        public ReducerRecommendation {
            decision = decision == null ? AdmissionDecision.UNKNOWN : decision;
            confirmationScope = confirmationScope == null ? ConfirmationScope.UNKNOWN : confirmationScope;
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
            evidenceGaps = evidenceGaps == null ? List.of() : List.copyOf(evidenceGaps);
            nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
            boundaries = boundaries == null ? List.of() : List.copyOf(boundaries);
        }
    }

    public record ReplayCaseResult(
        String id,
        AdmissionDecision expectedDecision,
        AdmissionDecision actualDecision,
        ConfirmationScope expectedScope,
        ConfirmationScope actualScope,
        Boolean expectedProtocolMatch,
        boolean actualProtocolMatch,
        boolean passed,
        List<String> reasons
    ) {
        public ReplayCaseResult {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public record RecommendationReplayReport(
        String metadataVersion,
        String generatedAt,
        int cases,
        int passedCases,
        int failedCases,
        List<ReplayCaseResult> results
    ) {
        public RecommendationReplayReport {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }
}
