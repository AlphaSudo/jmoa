package com.yourorg.jmoa.plugin.runtimepolicy;

import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyAdmissionInput;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyDecision;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyRecommendation;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyRegistryEntry;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyScope;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RuntimePolicyRecommendationEngine {

    private static final List<String> BOUNDARIES = List.of(
        "The runtime-policy recommendation engine is report-only and never changes launch configuration.",
        "A confirmed policy applies only to the exact service, launch mode, runtime policy, and reducer engine in the registry.",
        "CDS recommendations require a fresh variant-specific archive and measured runtime mapping proof.",
        "No-CDS, CDS, AppCDS, and diagnostic runs are not interchangeable for runtime performance claims."
    );

    private final RuntimePolicyRegistry registry;

    public RuntimePolicyRecommendationEngine(RuntimePolicyRegistry registry) {
        this.registry = registry == null ? RuntimePolicyRegistry.empty() : registry;
    }

    public RuntimePolicyRecommendation recommend(RuntimePolicyAdmissionInput input) {
        Optional<RuntimePolicyRegistryEntry> match = registry.find(input);

        if (input.diagnosticOnly() || RuntimePolicyRegistry.normalize(input.runtimePolicy()).contains("DIAGNOSTIC")) {
            return result(input, match, RuntimePolicyDecision.ALLOW_DIAGNOSTIC_ONLY, false,
                List.of("The requested runtime policy is explicitly diagnostic-only."),
                List.of("claimable confirmation evidence"),
                List.of("Keep diagnostics separate from clean memory confirmation runs."));
        }

        if (failedScreen(input.screenVerdict())) {
            return result(input, match, RuntimePolicyDecision.BLOCK_RUNTIME_PROMOTION, false,
                List.of("The runtime screen failed its promotion gate."),
                List.of(),
                List.of("Do not promote this runtime policy; investigate with V2-D before another screen."));
        }

        if (comparisonPolicyMismatch(input)) {
            return result(input, match, RuntimePolicyDecision.BLOCK_POLICY_MISMATCH, false,
                List.of("The comparison mixes runtime policies: " + input.comparisonRuntimePolicy()
                    + " versus " + input.runtimePolicy() + "."),
                List.of(),
                List.of("Compare baseline and candidate under the same runtime policy."));
        }

        if (Boolean.FALSE.equals(input.runtimeStackAvailable())) {
            return result(input, match, RuntimePolicyDecision.BLOCK_RUNTIME_STACK_MISSING, false,
                List.of("The required runtime stack or materialized deployment is unavailable."),
                List.of("runtime stack", "materialized runtime artifact"),
                List.of("Restore the runtime stack before taking semantic or memory evidence."));
        }

        if (isCdsPolicy(input.runtimePolicy())) {
            List<String> cdsGaps = cdsGaps(input, match.orElse(null));
            if (!cdsGaps.isEmpty()) {
                return result(input, match, RuntimePolicyDecision.BLOCK_CDS_ARCHIVE_MISMATCH, false,
                    List.of("The CDS archive cannot be proven fresh and matched to the requested artifact."),
                    cdsGaps,
                    List.of("Train a fresh variant-specific CDS archive and capture runtime mapping proof."));
            }
        } else if (isNoCdsPolicy(input.runtimePolicy()) && noCdsPolicyMismatch(input)) {
            return result(input, match, RuntimePolicyDecision.BLOCK_POLICY_MISMATCH, false,
                List.of("The requested no-CDS policy conflicts with enabled CDS/AppCDS/Leyden or a runtime javaagent."),
                List.of(),
                List.of("Disable CDS/AppCDS/Leyden and the runtime javaagent, then recapture evidence."));
        } else if (isNoCdsPolicy(input.runtimePolicy())) {
            List<String> noCdsGaps = noCdsGaps(input);
            if (!noCdsGaps.isEmpty()) {
                return result(input, match, RuntimePolicyDecision.RECOMMEND_SCREEN_REQUIRED, false,
                    List.of("The requested no-CDS policy is missing explicit disabled-state evidence."),
                    noCdsGaps,
                    List.of("Record the CDS/AppCDS/Leyden/javaagent off-state before screening or confirming memory."));
            }
        }

        if (!input.artifactEvidencePresent()) {
            return result(input, match, RuntimePolicyDecision.UNKNOWN, false,
                List.of("No artifact or materialization evidence was supplied."),
                List.of("artifact or deployment materialization proof"),
                List.of("Produce a materialization proof before requesting a runtime policy recommendation."));
        }

        if (match.isPresent() && match.get().materializationProofRequired()
            && !Boolean.TRUE.equals(input.runtimeMaterializationProofPresent())) {
            return result(input, match, RuntimePolicyDecision.RECOMMEND_SCREEN_REQUIRED, false,
                List.of("The registered protocol requires runtime materialization proof before policy admission."),
                List.of("runtime materialization proof"),
                List.of("Verify that the measured deployment used the intended artifact before screening or confirming memory."));
        }

        if (Boolean.FALSE.equals(input.semanticSmokePassed())) {
            return result(input, match, RuntimePolicyDecision.BLOCK_NO_SEMANTIC_SMOKE, false,
                List.of("Semantic smoke failed for the requested runtime policy."),
                List.of(),
                List.of("Fix the runtime failure before collecting policy confirmation evidence."));
        }

        if (input.semanticSmokePassed() == null) {
            if (input.preflight()) {
                return result(input, match, RuntimePolicyDecision.BLOCK_NO_SEMANTIC_SMOKE, false,
                    List.of("Preflight cannot recommend a runtime policy without semantic smoke evidence."),
                    List.of("semantic smoke"),
                    List.of("Start the materialized artifact and run semantic smoke before screening memory."));
            }
            return result(input, match, RuntimePolicyDecision.ALLOW_ARTIFACT_ONLY, false,
                List.of("Artifact evidence is present, but semantic runtime evidence is unavailable."),
                List.of("semantic smoke", "V2-C confirmation", "V2-D attribution"),
                List.of("Do not use this as a runtime recommendation until semantic smoke is available."));
        }

        if (!input.hasV2CConfirmation() || !"CONFIRMED_WIN".equals(RuntimePolicyRegistry.normalize(input.v2cVerdict()))) {
            return result(input, match, RuntimePolicyDecision.BLOCK_NO_V2C_CONFIRMATION, false,
                List.of("V2-C has not confirmed a runtime win for the requested policy: " + input.v2cVerdict() + "."),
                List.of("V2-C confirmed paired evidence"),
                List.of("Run a valid screen and paired confirmation under this exact policy."));
        }

        if (!input.hasV2DAttribution()) {
            return result(input, match, RuntimePolicyDecision.RECOMMEND_SCREEN_REQUIRED, false,
                List.of("V2-C confirmed the metric result, but V2-D attribution is missing."),
                List.of("V2-D attribution"),
                List.of("Run V2-D before registering this policy as confirmed."));
        }

        if (input.preflight() && input.artifactSha256().isBlank() && !isCdsPolicy(input.runtimePolicy())) {
            return result(input, match, RuntimePolicyDecision.RECOMMEND_SCREEN_REQUIRED, false,
                List.of("The policy is known, but preflight lacks an artifact SHA-256 or deployment-layer fingerprint."),
                List.of("artifact SHA-256 or deployment-layer fingerprint"),
                List.of("Record an artifact fingerprint before relying on the historical policy scope."));
        }

        if (match.isEmpty()) {
            return result(input, match, RuntimePolicyDecision.RECOMMEND_SCREEN_REQUIRED, false,
                List.of("No exact confirmed runtime-policy registry entry matches the requested context."),
                List.of("registered policy confirmation"),
                List.of("Run semantic smoke, V2-C confirmation, and V2-D attribution for this policy before admission."));
        }

        return result(input, match, RuntimePolicyDecision.RECOMMEND_CONFIRMED_POLICY, true,
            List.of(
                "Artifact and runtime materialization evidence are present.",
                "Semantic smoke passed.",
                "V2-C confirmed a runtime win.",
                "V2-D attribution is present.",
                "The requested context matches registered protocol " + match.get().id() + "."
            ),
            List.of(),
            List.of("Use this policy only within the registered scope.",
                "Require fresh V2-C/V2-D evidence for any artifact, engine, launch-mode, or runtime-policy change."));
    }

    private RuntimePolicyRecommendation result(
        RuntimePolicyAdmissionInput input,
        Optional<RuntimePolicyRegistryEntry> match,
        RuntimePolicyDecision decision,
        boolean promotionAllowed,
        List<String> reasons,
        List<String> missingGates,
        List<String> nextActions
    ) {
        RuntimePolicyScope scope = match.map(RuntimePolicyRegistryEntry::scope).orElse(input.scope());
        return new RuntimePolicyRecommendation(
            "v2n-runtime-policy-recommendation",
            Instant.now().toString(),
            decision,
            scope,
            match.isPresent(),
            promotionAllowed,
            match.map(RuntimePolicyRegistryEntry::id).orElse(""),
            reasons,
            missingGates,
            nextActions,
            input,
            BOUNDARIES
        );
    }

    private static List<String> cdsGaps(
        RuntimePolicyAdmissionInput input,
        RuntimePolicyRegistryEntry matchedProtocol
    ) {
        List<String> gaps = new ArrayList<>();
        if (!Boolean.TRUE.equals(input.cdsEnabled())) {
            gaps.add("CDS enabled runtime policy");
        }
        if (input.artifactSha256().isBlank()) {
            gaps.add("artifact SHA-256");
        }
        if (input.cdsArchiveSha256().isBlank()) {
            gaps.add("CDS archive SHA-256");
        }
        if (!Boolean.TRUE.equals(input.runtimeMaterializationProofPresent())) {
            gaps.add("runtime materialization proof");
        }
        if (!Boolean.TRUE.equals(input.cdsMappedAtRuntime())) {
            gaps.add("CDS mapped-at-runtime proof");
        }
        if (Boolean.TRUE.equals(input.javaagentPresent())) {
            gaps.add("runtime javaagent must be absent");
        }
        if (matchedProtocol != null && matchedProtocol.variantSpecificCdsRequired()) {
            if (!matchedProtocol.artifactSha256().isBlank()
                && !sameHash(matchedProtocol.artifactSha256(), input.artifactSha256())) {
                gaps.add("artifact hash does not match the archive-trained variant");
            }
            if (!matchedProtocol.cdsArchiveSha256().isBlank()
                && !sameHash(matchedProtocol.cdsArchiveSha256(), input.cdsArchiveSha256())) {
                gaps.add("CDS archive hash does not match the registered variant-specific archive");
            }
        }
        return gaps;
    }

    private static boolean noCdsPolicyMismatch(RuntimePolicyAdmissionInput input) {
        return Boolean.TRUE.equals(input.cdsEnabled())
            || Boolean.TRUE.equals(input.appCdsEnabled())
            || Boolean.TRUE.equals(input.leydenEnabled())
            || Boolean.TRUE.equals(input.javaagentPresent());
    }

    private static List<String> noCdsGaps(RuntimePolicyAdmissionInput input) {
        List<String> gaps = new ArrayList<>();
        if (input.cdsEnabled() == null) {
            gaps.add("CDS disabled-state evidence");
        }
        if (input.appCdsEnabled() == null) {
            gaps.add("AppCDS disabled-state evidence");
        }
        if (input.leydenEnabled() == null) {
            gaps.add("Leyden disabled-state evidence");
        }
        if (input.javaagentPresent() == null) {
            gaps.add("runtime javaagent absent-state evidence");
        }
        return gaps;
    }

    private static boolean comparisonPolicyMismatch(RuntimePolicyAdmissionInput input) {
        String comparison = RuntimePolicyRegistry.normalize(input.comparisonRuntimePolicy());
        return !comparison.isBlank() && !"UNKNOWN".equals(comparison)
            && !comparison.equals(RuntimePolicyRegistry.normalize(input.runtimePolicy()));
    }

    private static boolean failedScreen(String value) {
        String normalized = RuntimePolicyRegistry.normalize(value);
        return normalized.contains("FAILED") || normalized.contains("REGRESSION")
            || normalized.contains("DO_NOT_PROMOTE") || normalized.contains("BLOCKED");
    }

    private static boolean isCdsPolicy(String value) {
        String policy = RuntimePolicyRegistry.normalize(value);
        return policy.equals("CDS") || (policy.contains("CDS") && !policy.contains("NO_CDS"));
    }

    private static boolean isNoCdsPolicy(String value) {
        return RuntimePolicyRegistry.normalize(value).contains("NO_CDS");
    }

    private static boolean sameHash(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }
}
