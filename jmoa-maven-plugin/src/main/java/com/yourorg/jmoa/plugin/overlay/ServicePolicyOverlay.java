package com.yourorg.jmoa.plugin.overlay;

import java.util.Set;

/**
 * Typed, service-agnostic view of a service policy overlay document. The
 * universal engine validates this shape; the service contributes the values.
 * An overlay may only select supported universal modes and guardrails — it
 * cannot introduce new policy vocabulary into core code.
 */
public record ServicePolicyOverlay(
        String schemaVersion,
        String service,
        String sourceRevision,
        String profileSha256,
        String configTreeSha256,
        String safeSamsSha256,
        String workloadId,
        AllowedModes allowedModes,
        Guardrails guardrails) {

    public record AllowedModes(
            Set<String> tier1AdapterModes,
            Set<String> tier2Consolidations,
            Set<String> reducerOperations,
            Set<String> reducerJarLayouts,
            Set<String> packagingModes) {
    }

    public record Guardrails(
            int requiredSemanticRequests,
            boolean requireZeroWorkloadErrors,
            boolean requireMutationProof,
            boolean requireZeroFallback,
            boolean admissionManifestRequired,
            boolean requireProtectedComponentAudit,
            boolean claimDiagnosticsSeparately) {
    }
}
