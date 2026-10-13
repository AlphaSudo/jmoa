package com.yourorg.jmoa.plugin.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Universal overlay validation. The engine owns the supported policy
 * vocabulary; an overlay may only narrow it. Validation fails closed on
 * unknown schema versions, unknown fields, unsupported modes, malformed
 * identities, and weakened guardrails.
 */
public final class ServicePolicyOverlayValidator {

    public static final Set<String> SUPPORTED_SCHEMA_VERSIONS =
            Set.of("jmoa-service-policy-overlay-v1", "jmoa-service-policy-overlay-v2");
    public static final String SUPPORTED_SCHEMA_VERSION = "jmoa-service-policy-overlay-v1";
    public static final Set<String> SUPPORTED_TIER1_ADAPTER_MODES = Set.of("RUNTIME", "DIRECT");
    public static final Set<String> SUPPORTED_TIER2_CONSOLIDATIONS =
            Set.of("CURRENT", "PACKAGE", "PACKAGE_SAM", "PACKAGE_SIGNATURE", "ROOT_PACKAGE_SIGNATURE");
    public static final Set<String> SUPPORTED_REDUCER_OPERATIONS = Set.of("repack-only", "strip");
    public static final Set<String> SUPPORTED_REDUCER_JAR_LAYOUTS = Set.of("current", "stable-raw-copy");
    public static final Set<String> SUPPORTED_PACKAGING_MODES = Set.of("EXPLODED_BOOT_APP", "FAT_BOOT_JAR");

    private static final Pattern SHA256 = Pattern.compile("^[0-9A-Fa-f]{64}$");
    private static final Pattern GIT_REVISION = Pattern.compile("^[0-9a-f]{40}$");

    public ValidationResult validate(ServicePolicyOverlayLoader.LoadedOverlay loaded) {
        List<PolicyDecision> decisions = new ArrayList<>();
        ServicePolicyOverlay overlay = loaded.overlay();
        if (overlay == null) {
            decisions.add(new PolicyDecision(PolicyDecisionReason.MISSING_REQUIRED_FIELD, "$document", "overlay document is not an object"));
            return new ValidationResult(false, List.copyOf(decisions));
        }
        if (!SUPPORTED_SCHEMA_VERSIONS.contains(overlay.schemaVersion())) {
            decisions.add(new PolicyDecision(PolicyDecisionReason.UNKNOWN_SCHEMA_VERSION, "schemaVersion",
                    "supported: " + SUPPORTED_SCHEMA_VERSIONS + ", got: " + overlay.schemaVersion()));
        }
        for (String field : List.of("service", "workloadId")) {
            if (isBlank(field(overlay, field))) {
                decisions.add(new PolicyDecision(PolicyDecisionReason.MISSING_REQUIRED_FIELD, field, "required non-empty text"));
            }
        }
        if (overlay.sourceRevision() == null || !GIT_REVISION.matcher(overlay.sourceRevision()).matches()) {
            decisions.add(new PolicyDecision(PolicyDecisionReason.INVALID_SOURCE_REVISION, "sourceRevision",
                    "must be a 40-character lowercase Git revision"));
        }
        for (String field : List.of("profileSha256", "configTreeSha256", "safeSamsSha256")) {
            String value = field(overlay, field);
            if (value == null || !SHA256.matcher(value).matches()) {
                decisions.add(new PolicyDecision(PolicyDecisionReason.INVALID_SHA256, field, "must be a 64-digit SHA-256 value"));
            }
        }
        for (String unknown : loaded.unknownTopLevelFields()) {
            decisions.add(new PolicyDecision(PolicyDecisionReason.UNKNOWN_POLICY_FIELD, unknown, "unknown top-level policy field"));
        }
        for (String unknown : loaded.unknownAllowedModeFields()) {
            decisions.add(new PolicyDecision(PolicyDecisionReason.UNKNOWN_POLICY_FIELD, "allowedModes." + unknown, "unknown allowed-modes field"));
        }
        for (String unknown : loaded.unknownGuardrailFields()) {
            decisions.add(new PolicyDecision(PolicyDecisionReason.UNKNOWN_POLICY_FIELD, "guardrails." + unknown, "unknown guardrail field"));
        }
        ServicePolicyOverlay.AllowedModes modes = overlay.allowedModes();
        if (modes == null) {
            decisions.add(new PolicyDecision(PolicyDecisionReason.MISSING_REQUIRED_FIELD, "allowedModes", "required policy selection"));
        } else {
            decisions.addAll(checkModes("tier1AdapterModes", modes.tier1AdapterModes(), SUPPORTED_TIER1_ADAPTER_MODES, PolicyDecisionReason.UNSUPPORTED_TIER1_ADAPTER_MODE));
            decisions.addAll(checkModes("tier2Consolidations", modes.tier2Consolidations(), SUPPORTED_TIER2_CONSOLIDATIONS, PolicyDecisionReason.UNSUPPORTED_TIER2_CONSOLIDATION));
            decisions.addAll(checkModes("reducerOperations", modes.reducerOperations(), SUPPORTED_REDUCER_OPERATIONS, PolicyDecisionReason.UNSUPPORTED_REDUCER_OPERATION));
            decisions.addAll(checkModes("reducerJarLayouts", modes.reducerJarLayouts(), SUPPORTED_REDUCER_JAR_LAYOUTS, PolicyDecisionReason.UNSUPPORTED_REDUCER_JAR_LAYOUT));
            decisions.addAll(checkModes("packagingModes", modes.packagingModes(), SUPPORTED_PACKAGING_MODES, PolicyDecisionReason.UNSUPPORTED_PACKAGING_MODE));
        }
        ServicePolicyOverlay.Guardrails guardrails = overlay.guardrails();
        if (guardrails == null) {
            decisions.add(new PolicyDecision(PolicyDecisionReason.MISSING_REQUIRED_FIELD, "guardrails", "required guardrail selection"));
        } else {
            if (guardrails.requiredSemanticRequests() < 1) {
                decisions.add(new PolicyDecision(PolicyDecisionReason.INVALID_SEMANTIC_REQUEST_COUNT, "guardrails.requiredSemanticRequests", "must be at least 1"));
            }
            if (!guardrails.requireZeroWorkloadErrors()) {
                decisions.add(new PolicyDecision(PolicyDecisionReason.WEAKENED_GUARDRAIL, "guardrails.requireZeroWorkloadErrors", "universal guardrail cannot be disabled"));
            }
            if (!guardrails.requireMutationProof()) {
                decisions.add(new PolicyDecision(PolicyDecisionReason.WEAKENED_GUARDRAIL, "guardrails.requireMutationProof", "universal guardrail cannot be disabled"));
            }
            if (!guardrails.requireZeroFallback()) {
                decisions.add(new PolicyDecision(PolicyDecisionReason.WEAKENED_GUARDRAIL, "guardrails.requireZeroFallback", "universal guardrail cannot be disabled"));
            }
            if (!guardrails.requireProtectedComponentAudit()) {
                decisions.add(new PolicyDecision(PolicyDecisionReason.WEAKENED_GUARDRAIL, "guardrails.requireProtectedComponentAudit", "universal guardrail cannot be disabled"));
            }
            if (!guardrails.claimDiagnosticsSeparately()) {
                decisions.add(new PolicyDecision(PolicyDecisionReason.WEAKENED_GUARDRAIL, "guardrails.claimDiagnosticsSeparately", "universal guardrail cannot be disabled"));
            }
        }
        boolean valid = decisions.isEmpty();
        return new ValidationResult(valid, List.copyOf(decisions));
    }

    public PolicyDecision decideTier1AdapterMode(ServicePolicyOverlay overlay, String mode) {
        return decideMode("tier1AdapterModes", mode, overlay.allowedModes() == null ? null : overlay.allowedModes().tier1AdapterModes());
    }

    public PolicyDecision decideTier2Consolidation(ServicePolicyOverlay overlay, String consolidation) {
        return decideMode("tier2Consolidations", consolidation, overlay.allowedModes() == null ? null : overlay.allowedModes().tier2Consolidations());
    }

    public PolicyDecision decideReducerOperation(ServicePolicyOverlay overlay, String operation) {
        return decideMode("reducerOperations", operation, overlay.allowedModes() == null ? null : overlay.allowedModes().reducerOperations());
    }

    public PolicyDecision decideReducerJarLayout(ServicePolicyOverlay overlay, String layout) {
        return decideMode("reducerJarLayouts", layout, overlay.allowedModes() == null ? null : overlay.allowedModes().reducerJarLayouts());
    }

    public PolicyDecision decidePackagingMode(ServicePolicyOverlay overlay, String packagingMode) {
        return decideMode("packagingModes", packagingMode, overlay.allowedModes() == null ? null : overlay.allowedModes().packagingModes());
    }

    private PolicyDecision decideMode(String fieldName, String requested, Set<String> allowedByOverlay) {
        if (requested == null || requested.isBlank()) {
            return new PolicyDecision(PolicyDecisionReason.MISSING_REQUIRED_FIELD, fieldName, "requested mode is empty");
        }
        if (allowedByOverlay == null) {
            return new PolicyDecision(PolicyDecisionReason.MISSING_REQUIRED_FIELD, fieldName, "overlay has no allowedModes");
        }
        if (!allowedByOverlay.contains(requested)) {
            return new PolicyDecision(PolicyDecisionReason.MODE_NOT_ALLOWED_BY_OVERLAY, fieldName,
                    "overlay allows " + allowedByOverlay + "; requested " + requested);
        }
        return new PolicyDecision(PolicyDecisionReason.ACCEPTED, fieldName, requested);
    }

    private static List<PolicyDecision> checkModes(String fieldName, Set<String> values, Set<String> supported, PolicyDecisionReason reason) {
        List<PolicyDecision> decisions = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            decisions.add(new PolicyDecision(PolicyDecisionReason.MISSING_REQUIRED_FIELD, "allowedModes." + fieldName, "at least one mode is required"));
            return decisions;
        }
        for (String value : values) {
            if (!supported.contains(value)) {
                decisions.add(new PolicyDecision(reason, "allowedModes." + fieldName,
                        "supported " + supported + "; overlay selects " + value));
            }
        }
        return decisions;
    }

    private static String field(ServicePolicyOverlay overlay, String name) {
        return switch (name) {
            case "service" -> overlay.service();
            case "workloadId" -> overlay.workloadId();
            case "sourceRevision" -> overlay.sourceRevision();
            case "profileSha256" -> overlay.profileSha256();
            case "configTreeSha256" -> overlay.configTreeSha256();
            case "safeSamsSha256" -> overlay.safeSamsSha256();
            default -> null;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record PolicyDecision(PolicyDecisionReason reason, String field, String detail) {
        public boolean accepted() {
            return reason == PolicyDecisionReason.ACCEPTED;
        }
    }

    public record ValidationResult(boolean valid, List<PolicyDecision> decisions) {
    }
}
