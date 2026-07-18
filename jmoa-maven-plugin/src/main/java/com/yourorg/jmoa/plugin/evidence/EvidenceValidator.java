package com.yourorg.jmoa.plugin.evidence;

import com.yourorg.jmoa.plugin.evidence.EvidenceModels.CdsMode;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceConfig;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.MemoryMetrics;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RunEvidence;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.RuntimePolicy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class EvidenceValidator {

    public RunEvidence validate(RunEvidence evidence, EvidenceConfig config) {
        List<String> invalid = new ArrayList<>(evidence.invalidReasons());
        List<String> warnings = new ArrayList<>(evidence.warnings());

        if (config.requireWorkloadZeroErrors()
            && evidence.workload().present()
            && evidence.workload().errors() > 0) {
            invalid.add("workload errors > 0");
        }
        if (evidence.workload().present()
            && evidence.workload().health() != null
            && !"UP".equalsIgnoreCase(evidence.workload().health())) {
            invalid.add("health is not UP");
        }
        if (evidence.capture().smapsRollup() == null || !new File(evidence.capture().smapsRollup()).isFile()) {
            invalid.add("missing post smaps_rollup");
        }
        if (evidence.capture().memoryCurrent() == null || !new File(evidence.capture().memoryCurrent()).isFile()) {
            invalid.add("missing memory.current");
        }
        if (config.requireArtifactHashes()
            && evidence.manifest().expectedArtifactSha256() != null
            && evidence.manifest().artifactSha256() != null
            && !evidence.manifest().expectedArtifactSha256().equalsIgnoreCase(evidence.manifest().artifactSha256())) {
            invalid.add("artifact hash mismatch");
        }
        validateRuntimePolicy(evidence, config, invalid);
        validateMemoryArithmetic(evidence.memory(), config, invalid);
        validateRuntimeGate(evidence, invalid, warnings);

        if (evidence.perturbation().diagnosticOnly() && config.markPerturbingDiagnostics()) {
            warnings.addAll(evidence.perturbation().warnings());
        }

        return new RunEvidence(
            evidence.manifest(),
            evidence.capture(),
            evidence.memory(),
            evidence.nmt(),
            evidence.heapInfo(),
            evidence.classHistogram(),
            evidence.workload(),
            evidence.runtimeVerification(),
            evidence.perturbation(),
            invalid.isEmpty(),
            invalid,
            warnings
        );
    }

    private static void validateRuntimePolicy(RunEvidence evidence, EvidenceConfig config, List<String> invalid) {
        RuntimePolicy expected = config.expectedPolicy();
        if (expected == RuntimePolicy.UNKNOWN || expected == null) {
            return;
        }
        RuntimePolicy actual = evidence.manifest().runtimePolicy();
        if (actual != RuntimePolicy.UNKNOWN && actual != expected) {
            invalid.add("runtime policy mismatch: expected " + expected + " but found " + actual);
        }
        if ((expected == RuntimePolicy.NO_CDS || expected == RuntimePolicy.NO_CDS_LOW_DIRTY)
            && evidence.manifest().cdsMode() != CdsMode.UNKNOWN
            && evidence.manifest().cdsMode() != CdsMode.OFF) {
            invalid.add("CDS mode mismatch: expected OFF but found " + evidence.manifest().cdsMode());
        }
        if ((expected == RuntimePolicy.NO_CDS || expected == RuntimePolicy.NO_CDS_LOW_DIRTY)
            && evidence.manifest().javaagentPresent()) {
            invalid.add("javaagent present while policy forbids javaagent");
        }
        if (expected == RuntimePolicy.JDK_BASE_CDS_LOW_DIRTY
            && evidence.manifest().cdsMode() != CdsMode.ON
            && evidence.manifest().cdsMode() != CdsMode.AUTO) {
            invalid.add("CDS mode mismatch: JDK_BASE_CDS_LOW_DIRTY requires ON or AUTO");
        }
        if (expected == RuntimePolicy.JDK_BASE_CDS_LOW_DIRTY && evidence.manifest().javaagentPresent()) {
            invalid.add("javaagent present while JDK_BASE_CDS_LOW_DIRTY forbids javaagent");
        }
        if (expected == RuntimePolicy.NO_CDS_LOW_DIRTY
            && evidence.manifest().mallocArenaMax() != null
            && !"1".equals(evidence.manifest().mallocArenaMax())) {
            invalid.add("NO_CDS_LOW_DIRTY expected MALLOC_ARENA_MAX=1");
        }
        if (expected == RuntimePolicy.JDK_BASE_CDS_LOW_DIRTY
            && !"1".equals(evidence.manifest().mallocArenaMax())) {
            invalid.add("JDK_BASE_CDS_LOW_DIRTY expected MALLOC_ARENA_MAX=1");
        }
    }

    private static void validateMemoryArithmetic(MemoryMetrics memory, EvidenceConfig config, List<String> invalid) {
        if (memory.rssKb() <= 0 && memory.pssKb() <= 0) {
            invalid.add("missing or empty memory metrics");
            return;
        }
        if (memory.pssKb() > memory.rssKb() && memory.rssKb() > 0) {
            invalid.add("PSS > RSS");
        }
        if (config.requireSmapsArithmetic() && memory.rssKb() > 0) {
            long components = memory.privateCleanKb()
                + memory.privateDirtyKb()
                + memory.sharedCleanKb()
                + memory.sharedDirtyKb();
            if (components > 0 && Math.abs(memory.rssKb() - components) > 4) {
                invalid.add("RSS arithmetic mismatch");
            }
        }
    }

    private static void validateRuntimeGate(RunEvidence evidence, List<String> invalid, List<String> warnings) {
        if (!evidence.runtimeVerification().present()) {
            return;
        }
        if (evidence.runtimeVerification().missingAdapterRefs() > 0) {
            invalid.add("adapter refs missing > 0");
        }
        if (evidence.runtimeVerification().originalJarShadowing()) {
            invalid.add("original jar shadows optimized jar");
        }
        if (!evidence.runtimeVerification().dynamicOriginsVerified()) {
            warnings.add("dynamic runtime origin proof not present");
        }
    }
}
