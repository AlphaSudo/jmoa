package com.yourorg.jmoa.plugin.reducer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Computes the typed capability verdict for one reducer run. The frozen
 * runtime evidence is binding here: the isolated reducer-leg contrasts were
 * adverse, so layout/strip changes are an artifact capability and the RAM
 * capability is explicitly NOT_SUPPORTED until a predeclared loaded-byte
 * mechanism proves otherwise.
 */
public final class ReducerCapabilityVerdicts {

    public record VerdictEntry(ReducerCapabilityVerdict verdict, boolean supported, String rationale) {
    }

    public record CapabilityVerdictReport(
            String metadataVersion,
            String generatedAt,
            String operation,
            String jarLayout,
            boolean mutationEnabled,
            boolean reportOnly,
            long totalNetBytesSaved,
            List<VerdictEntry> capabilities,
            List<String> boundaries,
            ReducerReproducibilityProof.ProofRecord reproducibilityProof) {

        public CapabilityVerdictReport {
            capabilities = List.copyOf(capabilities);
            boundaries = List.copyOf(boundaries);
        }

        public boolean supports(ReducerCapabilityVerdict verdict) {
            return capabilities.stream().filter(e -> e.verdict() == verdict).findFirst().map(VerdictEntry::supported).orElse(false);
        }
    }

    public static CapabilityVerdictReport compute(ReducerReport report) {
        return compute(report, null);
    }

    /**
     * Chapter-29 F7 fix: REPRODUCIBLE_LAYOUT_WIN requires an ATTACHED
     * two-build proof record that actually compared clean builds in distinct
     * directories. A layout name alone proves nothing.
     */
    public static CapabilityVerdictReport compute(ReducerReport report, ReducerReproducibilityProof.ProofRecord proof) {
        List<VerdictEntry> capabilities = new ArrayList<>();
        boolean sizeWin = report.totalNetBytesSaved() > 0L;
        capabilities.add(new VerdictEntry(
                ReducerCapabilityVerdict.ARTIFACT_SIZE_WIN,
                sizeWin,
                sizeWin
                        ? "net archive bytes saved: " + report.totalNetBytesSaved()
                        : "no net archive-byte reduction: " + report.totalNetBytesSaved()));
        boolean stableLayout = "stable-raw-copy".equals(report.jarLayout());
        boolean classMutationRequested = !"repack-only".equals(report.operation());
        boolean mutationSafetySatisfied = !classMutationRequested || !report.rawClassAudits().isEmpty();
        boolean proofPassed = proof != null && proof.passed();
        boolean reproducibleWin = stableLayout && mutationSafetySatisfied && proofPassed;
        String layoutRationale;
        if (!stableLayout) {
            layoutRationale = "reproducible layout requires jarLayout=stable-raw-copy; got " + report.jarLayout();
        } else if (!mutationSafetySatisfied) {
            layoutRationale = "operation=" + report.operation()
                    + " permits class mutation and therefore requires raw preservation audits; got "
                    + report.rawClassAudits().size();
        } else if (proof == null) {
            layoutRationale = "stable layout with mutation safety satisfied, but no attached two-build reproducibility proof; "
                    + "the verdict cannot be claimed without one";
        } else if (!proof.passed()) {
            layoutRationale = "two-build reproducibility proof FAILED: " + proof.differingEntries();
        } else {
            layoutRationale = "two-build proof passed: " + proof.jarCount() + " jars, " + proof.entryCount()
                    + " entries byte-identical across distinct directories " + proof.buildARoot() + " and " + proof.buildBRoot()
                    + (classMutationRequested
                            ? "; raw preservation audits cover the requested class mutation"
                            : "; repack-only requested no class mutation, so zero raw class mutation audits is valid");
        }
        capabilities.add(new VerdictEntry(
                ReducerCapabilityVerdict.REPRODUCIBLE_LAYOUT_WIN,
                reproducibleWin,
                layoutRationale));
        // Frozen runtime evidence: isolated strip/layout legs were adverse and
        // compressed disk bytes are not resident pages one-for-one. This is a
        // statement of current evidence, not a permanent property.
        capabilities.add(new VerdictEntry(
                ReducerCapabilityVerdict.RAM_NOT_SUPPORTED,
                true,
                "reducer/layout changes are not a RAM capability: isolated runtime legs were adverse; "
                        + "RAM reopening requires a predeclared loaded-class/resident-byte mechanism"));
        return new CapabilityVerdictReport(
                "v2-reducer-capability-verdict",
                report.generatedAt(),
                report.operation(),
                report.jarLayout(),
                report.mutationEnabled(),
                report.reportOnly(),
                report.totalNetBytesSaved(),
                capabilities,
                List.of(
                        "artifact size and resident memory are independent capabilities",
                        "LVT/LVTT removal is the only mutation permitted by the raw safety policy",
                        "class removal belongs to generated-orphan pruning, not strip",
                        "reproducible layout claims require an attached two-build proof"),
                proof);
    }

    public static Map<String, Object> toJsonView(CapabilityVerdictReport report) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("metadataVersion", report.metadataVersion());
        view.put("generatedAt", report.generatedAt());
        view.put("operation", report.operation());
        view.put("jarLayout", report.jarLayout());
        view.put("mutationEnabled", report.mutationEnabled());
        view.put("reportOnly", report.reportOnly());
        view.put("totalNetBytesSaved", report.totalNetBytesSaved());
        view.put("capabilities", report.capabilities());
        view.put("boundaries", report.boundaries());
        view.put("unsupportedRawZipFields", ReducerReproducibilityProof.UNSUPPORTED_RAW_ZIP_FIELDS);
        if (report.reproducibilityProof() != null) {
            view.put("reproducibilityProof", report.reproducibilityProof());
        }
        return view;
    }

    private ReducerCapabilityVerdicts() {
    }
}
