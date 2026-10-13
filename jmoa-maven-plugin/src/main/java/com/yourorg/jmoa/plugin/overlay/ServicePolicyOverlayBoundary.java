package com.yourorg.jmoa.plugin.overlay;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The single production boundary between the universal optimizer and a
 * service policy overlay. When a build configures an overlay, every mode
 * decision passes through here: the overlay is loaded, validated (unknown
 * fields and weakened guardrails are refused), the configured Mojo modes are
 * parsed into typed enums, and each mode must be explicitly allowed by the
 * overlay. The resulting effective-policy report is embedded in the build
 * summary so every candidate build carries its overlay identity.
 */
public final class ServicePolicyOverlayBoundary {

    public record OverlayPolicyReport(
            String overlayPath,
            String overlaySha256,
            String schemaVersion,
            String serviceName,
            boolean valid,
            List<ServicePolicyOverlayValidator.PolicyDecision> validationDecisions,
            List<String> acceptedFields,
            List<String> rejectedFields,
            Map<String, String> effectiveModes,
            Map<String, Boolean> guardrails) {
    }

    public static OverlayPolicyReport enforce(
            File overlayPath,
            Consumer<String> info,
            String tier1AdapterMode,
            String tier2Consolidation) {
        Context context = loadAndValidate(overlayPath, info);
        PluginModeEnums.Tier1AdapterMode tier1 = PluginModeEnums.Tier1AdapterMode.parse(tier1AdapterMode);
        PluginModeEnums.Tier2ConsolidationMode tier2 = PluginModeEnums.Tier2ConsolidationMode.parse(tier2Consolidation);
        context.require(context.validator().decideTier1AdapterMode(context.overlay(), tier1.name()));
        context.require(context.validator().decideTier2Consolidation(context.overlay(), tier2.name()));
        context.effectiveModes().put("tier1AdapterMode", tier1.name());
        context.effectiveModes().put("tier2Consolidation", tier2.name());
        info.accept("Service policy overlay enforced for " + context.serviceName()
                + ": tier1=" + tier1 + ", tier2=" + tier2);
        return context.toReport();
    }

    public static OverlayPolicyReport enforceReducer(
            File overlayPath,
            Consumer<String> info,
            String reducerOperation,
            String reducerJarLayout) {
        Context context = loadAndValidate(overlayPath, info);
        PluginModeEnums.ReducerOperationMode operation = PluginModeEnums.ReducerOperationMode.parse(reducerOperation);
        PluginModeEnums.ReducerJarLayoutMode layout = PluginModeEnums.ReducerJarLayoutMode.parse(reducerJarLayout);
        context.require(context.validator().decideReducerOperation(context.overlay(), operation.wireName()));
        context.require(context.validator().decideReducerJarLayout(context.overlay(), layout.wireName()));
        context.effectiveModes().put("reducerOperation", operation.wireName());
        context.effectiveModes().put("reducerJarLayout", layout.wireName());
        return context.toReport();
    }

    public static OverlayPolicyReport enforcePackaging(
            File overlayPath,
            Consumer<String> info,
            String packagingMode) {
        Context context = loadAndValidate(overlayPath, info);
        PluginModeEnums.PackagingMode packaging = PluginModeEnums.PackagingMode.parse(packagingMode);
        context.require(context.validator().decidePackagingMode(context.overlay(), packaging.name()));
        context.effectiveModes().put("packagingMode", packaging.name());
        return context.toReport();
    }

    /** Report-only view of an overlay (validation decisions, no mode enforcement). */
    public static OverlayPolicyReport report(File overlayPath) {
        return loadAndValidate(overlayPath, message -> { }).toReport();
    }

    private static final class Context {
        private final File overlayPath;
        private final ServicePolicyOverlay overlay;
        private final ServicePolicyOverlayValidator validator;
        private final ServicePolicyOverlayValidator.ValidationResult validation;
        private final List<ServicePolicyOverlayValidator.PolicyDecision> decisions = new java.util.ArrayList<>();
        private final Map<String, String> effectiveModes = new LinkedHashMap<>();
        private final Map<String, Boolean> guardrails = new LinkedHashMap<>();

        private Context(File overlayPath,
                ServicePolicyOverlay overlay,
                ServicePolicyOverlayValidator validator,
                ServicePolicyOverlayValidator.ValidationResult validation) {
            this.overlayPath = overlayPath;
            this.overlay = overlay;
            this.validator = validator;
            this.validation = validation;
            this.decisions.addAll(validation.decisions());
            ServicePolicyOverlay.AllowedModes allowed = overlay.allowedModes();
            if (allowed.tier1AdapterModes() != null) { effectiveModes.put("allowedTier1AdapterModes", String.join(",", allowed.tier1AdapterModes())); }
            if (allowed.tier2Consolidations() != null) { effectiveModes.put("allowedTier2Consolidations", String.join(",", allowed.tier2Consolidations())); }
            if (allowed.reducerOperations() != null) { effectiveModes.put("allowedReducerOperations", String.join(",", allowed.reducerOperations())); }
            if (allowed.reducerJarLayouts() != null) { effectiveModes.put("allowedReducerJarLayouts", String.join(",", allowed.reducerJarLayouts())); }
            if (allowed.packagingModes() != null) { effectiveModes.put("allowedPackagingModes", String.join(",", allowed.packagingModes())); }
            ServicePolicyOverlay.Guardrails g = overlay.guardrails();
            guardrails.put("requireZeroWorkloadErrors", g.requireZeroWorkloadErrors());
            guardrails.put("requireMutationProof", g.requireMutationProof());
            guardrails.put("requireZeroFallback", g.requireZeroFallback());
            guardrails.put("admissionManifestRequired", g.admissionManifestRequired());
            guardrails.put("requireProtectedComponentAudit", g.requireProtectedComponentAudit());
            guardrails.put("claimDiagnosticsSeparately", g.claimDiagnosticsSeparately());
        }

        ServicePolicyOverlay overlay() { return overlay; }
        ServicePolicyOverlayValidator validator() { return validator; }
        Map<String, String> effectiveModes() { return effectiveModes; }
        String serviceName() { return overlay.service(); }

        void require(ServicePolicyOverlayValidator.PolicyDecision decision) {
            decisions.add(decision);
            ServicePolicyOverlayBoundary.require(decision);
        }

        OverlayPolicyReport toReport() {
            return new OverlayPolicyReport(
                    overlayPath.getAbsolutePath(),
                    sha256(overlayPath),
                    overlay.schemaVersion(),
                    overlay.service(),
                    true,
                    List.copyOf(decisions),
                    List.of("schemaVersion", "service", "sourceRevision", "profileSha256", "configTreeSha256",
                            "safeSamsSha256", "workloadId", "allowedModes", "guardrails"),
                    decisions.stream().filter(decision -> !decision.accepted())
                            .map(ServicePolicyOverlayValidator.PolicyDecision::field).distinct().toList(),
                    new LinkedHashMap<>(effectiveModes),
                    new LinkedHashMap<>(guardrails));
        }
    }

    private static Context loadAndValidate(File overlayPath, Consumer<String> info) {
        ServicePolicyOverlayLoader.LoadedOverlay loaded;
        try {
            loaded = new ServicePolicyOverlayLoader().load(overlayPath.toPath());
        } catch (IOException e) {
            throw new IllegalArgumentException("Service policy overlay could not be read: " + overlayPath, e);
        }
        ServicePolicyOverlayValidator.ValidationResult result = new ServicePolicyOverlayValidator().validate(loaded);
        if (!result.valid()) {
            StringBuilder detail = new StringBuilder("Service policy overlay refused: " + overlayPath);
            for (ServicePolicyOverlayValidator.PolicyDecision decision : result.decisions()) {
                detail.append(System.lineSeparator()).append("  - ")
                        .append(decision.reason()).append(" at ").append(decision.field())
                        .append(": ").append(decision.detail());
            }
            throw new IllegalArgumentException(detail.toString());
        }
        info.accept("Service policy overlay validated: " + overlayPath.getAbsolutePath());
        return new Context(overlayPath, loaded.overlay(), new ServicePolicyOverlayValidator(), result);
    }

    private static void require(ServicePolicyOverlayValidator.PolicyDecision decision) {
        if (!decision.accepted()) {
            throw new IllegalArgumentException("Overlay policy refused mode selection: " + decision.reason()
                    + " at " + decision.field() + ": " + decision.detail());
        }
    }

    private static String sha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file.toPath()));
            return HexFormat.of().formatHex(hash).toUpperCase();
        } catch (Exception e) {
            return "UNAVAILABLE";
        }
    }
}
