package com.yourorg.jmoa.plugin.runtimepolicy;

import java.util.List;

public final class RuntimePolicyModels {

    private RuntimePolicyModels() {
    }

    public enum RuntimePolicyDecision {
        RECOMMEND_CONFIRMED_POLICY,
        RECOMMEND_SCREEN_REQUIRED,
        ALLOW_DIAGNOSTIC_ONLY,
        ALLOW_ARTIFACT_ONLY,
        BLOCK_POLICY_MISMATCH,
        BLOCK_CDS_ARCHIVE_MISMATCH,
        BLOCK_RUNTIME_STACK_MISSING,
        BLOCK_NO_SEMANTIC_SMOKE,
        BLOCK_NO_V2C_CONFIRMATION,
        BLOCK_RUNTIME_PROMOTION,
        UNKNOWN
    }

    public enum RuntimePolicyScope {
        PUBLIC,
        PRIVATE,
        INTERNAL,
        UNKNOWN
    }

    public enum RuntimeRecommendationMode {
        ANALYZE,
        PREFLIGHT,
        REPLAY
    }

    public record RuntimePolicyContext(
        String service,
        String launchMode,
        String runtimePolicy,
        String reducerEngine,
        String artifactSha256,
        String cdsArchiveSha256,
        RuntimePolicyScope scope,
        RuntimeRecommendationMode mode
    ) {
        public RuntimePolicyContext {
            scope = scope == null ? RuntimePolicyScope.UNKNOWN : scope;
            mode = mode == null ? RuntimeRecommendationMode.ANALYZE : mode;
        }
    }

    public record RuntimePolicyRegistryEntry(
        String id,
        String service,
        String launchMode,
        String runtimePolicy,
        String reducerEngine,
        RuntimePolicyScope scope,
        boolean cdsRequired,
        boolean variantSpecificCdsRequired,
        String artifactSha256,
        String cdsArchiveSha256,
        boolean materializationProofRequired,
        String claimStatus,
        String notes
    ) {
        public RuntimePolicyRegistryEntry {
            id = valueOr(id, "unnamed-protocol");
            service = valueOr(service, "unknown");
            launchMode = valueOr(launchMode, "UNKNOWN");
            runtimePolicy = valueOr(runtimePolicy, "UNKNOWN");
            reducerEngine = valueOr(reducerEngine, "unknown");
            scope = scope == null ? RuntimePolicyScope.UNKNOWN : scope;
            artifactSha256 = valueOr(artifactSha256, "");
            cdsArchiveSha256 = valueOr(cdsArchiveSha256, "");
            claimStatus = valueOr(claimStatus, "UNKNOWN");
            notes = valueOr(notes, "");
        }
    }

    public record RuntimePolicyAdmissionInput(
        String metadataVersion,
        String service,
        String launchMode,
        String runtimePolicy,
        String reducerEngine,
        String artifactSha256,
        String cdsArchiveSha256,
        String comparisonRuntimePolicy,
        Boolean cdsEnabled,
        Boolean appCdsEnabled,
        Boolean leydenEnabled,
        Boolean javaagentPresent,
        boolean artifactEvidencePresent,
        Boolean runtimeStackAvailable,
        Boolean semanticSmokePassed,
        Boolean runtimeMaterializationProofPresent,
        Boolean cdsMappedAtRuntime,
        boolean hasV2CConfirmation,
        String v2cVerdict,
        boolean hasV2DAttribution,
        String screenVerdict,
        boolean diagnosticOnly,
        boolean preflight,
        RuntimePolicyScope scope,
        List<String> sourceReports
    ) {
        public RuntimePolicyAdmissionInput {
            metadataVersion = valueOr(metadataVersion, "v2n-runtime-policy-admission-input");
            service = valueOr(service, "unknown");
            launchMode = valueOr(launchMode, "UNKNOWN");
            runtimePolicy = valueOr(runtimePolicy, "UNKNOWN");
            reducerEngine = valueOr(reducerEngine, "unknown");
            artifactSha256 = valueOr(artifactSha256, "");
            cdsArchiveSha256 = valueOr(cdsArchiveSha256, "");
            comparisonRuntimePolicy = valueOr(comparisonRuntimePolicy, "UNKNOWN");
            v2cVerdict = valueOr(v2cVerdict, "NO_CLAIM");
            screenVerdict = valueOr(screenVerdict, "NOT_RUN");
            scope = scope == null ? RuntimePolicyScope.UNKNOWN : scope;
            sourceReports = sourceReports == null ? List.of() : List.copyOf(sourceReports);
        }
    }

    public record RuntimePolicyRecommendation(
        String metadataVersion,
        String generatedAt,
        RuntimePolicyDecision decision,
        RuntimePolicyScope scope,
        boolean protocolMatchesRegistry,
        boolean runtimePolicyPromotionAllowed,
        String matchedProtocolId,
        List<String> reasons,
        List<String> missingGates,
        List<String> nextActions,
        RuntimePolicyAdmissionInput input,
        List<String> boundaries
    ) {
        public RuntimePolicyRecommendation {
            decision = decision == null ? RuntimePolicyDecision.UNKNOWN : decision;
            scope = scope == null ? RuntimePolicyScope.UNKNOWN : scope;
            matchedProtocolId = valueOr(matchedProtocolId, "");
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
            missingGates = missingGates == null ? List.of() : List.copyOf(missingGates);
            nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
            boundaries = boundaries == null ? List.of() : List.copyOf(boundaries);
        }
    }

    public record RuntimePolicyReplayCaseResult(
        String id,
        RuntimePolicyDecision expectedDecision,
        RuntimePolicyDecision actualDecision,
        RuntimePolicyScope expectedScope,
        RuntimePolicyScope actualScope,
        Boolean expectedProtocolMatch,
        boolean actualProtocolMatch,
        boolean passed,
        List<String> reasons
    ) {
        public RuntimePolicyReplayCaseResult {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public record RuntimePolicyReplayReport(
        String metadataVersion,
        String generatedAt,
        int cases,
        int passedCases,
        int failedCases,
        List<RuntimePolicyReplayCaseResult> results
    ) {
        public RuntimePolicyReplayReport {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
