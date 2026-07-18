package com.yourorg.jmoa.plugin.evidence;

import java.util.List;
import java.util.Map;

public final class EvidenceModels {

    private EvidenceModels() {
    }

    public enum Variant {
        BASELINE,
        CANDIDATE,
        DIAGNOSTIC,
        UNKNOWN
    }

    public enum RuntimePolicy {
        NO_CDS,
        CDS,
        NO_CDS_LOW_DIRTY,
        JDK_BASE_CDS_LOW_DIRTY,
        DIAGNOSTIC,
        UNKNOWN
    }

    public enum CdsMode {
        OFF,
        ON,
        AUTO,
        UNKNOWN
    }

    public enum LaunchMode {
        EXPLODED_BOOT_APP,
        SPRING_BOOT_FAT_JAR,
        EXPANDED_CLASSPATH,
        UNKNOWN
    }

    public enum Verdict {
        CONFIRMED_WIN,
        CONFIRMED_REGRESSION,
        MARGINAL_WIN_NEEDS_5PAIR,
        MIXED_METRICS_NEEDS_RERUN,
        INVALID_EVIDENCE,
        DIAGNOSTIC_ONLY,
        NO_CLAIM
    }

    public enum VarianceCategory {
        BASELINE_NOISE,
        CANDIDATE_NOISE,
        HEAP_PAGE_TOUCH,
        ANONYMOUS_RW_ALLOCATOR,
        CODECACHE_JIT,
        CGROUP_MEMORY_CURRENT_DIVERGENCE,
        STARTUP_ORDER,
        WORKLOAD_SHAPE,
        SUPPORT_SERVICE_NOISE,
        LAUNCH_MODE_SENSITIVITY,
        DIAGNOSTIC_PERTURBATION,
        ARTIFACT_MATERIALIZATION_INVALID,
        UNKNOWN
    }

    public record EvidenceConfig(
        RuntimePolicy expectedPolicy,
        boolean requireArtifactHashes,
        boolean requireWorkloadZeroErrors,
        boolean requireSmapsArithmetic,
        boolean failOnInvalidRun,
        boolean markPerturbingDiagnostics
    ) {
        public static EvidenceConfig defaults() {
            return new EvidenceConfig(RuntimePolicy.UNKNOWN, true, true, true, true, true);
        }
    }

    public record RunManifest(
        String runId,
        int pairIndex,
        Variant variant,
        String service,
        String phase,
        String artifactSha256,
        String expectedArtifactSha256,
        String imageId,
        String containerId,
        Integer pid,
        LaunchMode launchMode,
        RuntimePolicy runtimePolicy,
        CdsMode cdsMode,
        boolean javaagentPresent,
        String mallocArenaMax,
        String javaVersion,
        String workloadId,
        String timestampStart,
        String timestampPost,
        boolean classLoadLoggingEnabled,
        boolean jfrEnabled,
        String nmtMode,
        boolean gcRunBeforeCapture
    ) {
        public RunManifest {
            variant = variant == null ? Variant.UNKNOWN : variant;
            launchMode = launchMode == null ? LaunchMode.UNKNOWN : launchMode;
            runtimePolicy = runtimePolicy == null ? RuntimePolicy.UNKNOWN : runtimePolicy;
            cdsMode = cdsMode == null ? CdsMode.UNKNOWN : cdsMode;
        }
    }

    public record EvidenceCapture(
        String runId,
        String capturePoint,
        String smapsRollup,
        String smapsFull,
        String nmtSummary,
        String heapInfo,
        String classHistogram,
        String metaspace,
        String classloadLog,
        String workloadResult,
        String memoryCurrent,
        String runtimeVerification,
        String adapterValidation
    ) {
    }

    public record MemoryMetrics(
        long rssKb,
        long pssKb,
        long privateDirtyKb,
        long privateCleanKb,
        long sharedCleanKb,
        long sharedDirtyKb,
        long memoryCurrentBytes,
        long heapPssKb,
        long heapPrivateDirtyKb,
        long anonymousRwPssKb,
        long anonymousRwPrivateDirtyKb,
        long anonymousExecutablePssKb,
        long nativeLibraryPssKb,
        long mappedFilePssKb,
        long stackPssKb
    ) {
    }

    public record SmapsRegionSummary(
        Map<String, Long> pssKbByCategory,
        Map<String, Long> privateDirtyKbByCategory
    ) {
        public SmapsRegionSummary {
            pssKbByCategory = pssKbByCategory == null ? Map.of() : Map.copyOf(pssKbByCategory);
            privateDirtyKbByCategory = privateDirtyKbByCategory == null ? Map.of() : Map.copyOf(privateDirtyKbByCategory);
        }
    }

    public record NmtSummary(
        long totalCommittedKb,
        long javaHeapCommittedKb,
        long metaspaceCommittedKb,
        long classCommittedKb,
        long codeCommittedKb,
        long arenaChunkCommittedKb,
        long mallocKb,
        long mallocCount
    ) {
    }

    public record HeapInfo(long committedKb, long usedKb) {
    }

    public record ClassHistogramSummary(long instances, long bytes, int classCount) {
    }

    public record WorkloadResult(
        String health,
        int errors,
        int requests,
        boolean present
    ) {
    }

    public record RuntimeVerificationGate(
        boolean present,
        boolean dynamicOriginsVerified,
        boolean optimizedOriginsVerified,
        boolean runtimeLibPresent,
        boolean originalJarShadowing,
        int missingAdapterRefs,
        String launchMode
    ) {
    }

    public record PerturbationReport(
        boolean classLoadLoggingEnabled,
        boolean jfrEnabled,
        boolean nmtDetailEnabled,
        boolean gcRunBeforeCapture,
        boolean diagnosticOnly,
        List<String> warnings
    ) {
        public PerturbationReport {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record RunEvidence(
        RunManifest manifest,
        EvidenceCapture capture,
        MemoryMetrics memory,
        NmtSummary nmt,
        HeapInfo heapInfo,
        ClassHistogramSummary classHistogram,
        WorkloadResult workload,
        RuntimeVerificationGate runtimeVerification,
        PerturbationReport perturbation,
        boolean valid,
        List<String> invalidReasons,
        List<String> warnings
    ) {
        public RunEvidence {
            invalidReasons = invalidReasons == null ? List.of() : List.copyOf(invalidReasons);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record PairResult(
        int pair,
        String baselineRunId,
        String candidateRunId,
        long deltaPssKb,
        long deltaPrivateDirtyKb,
        long deltaMemoryCurrentBytes,
        long deltaHeapPssKb,
        long deltaAnonymousRwPssKb,
        long deltaAnonymousExecutablePssKb,
        long deltaHeapUsedKb,
        boolean pass,
        boolean valid,
        List<String> invalidReasons
    ) {
        public PairResult {
            invalidReasons = invalidReasons == null ? List.of() : List.copyOf(invalidReasons);
        }
    }

    public record ConfirmationReport(
        String candidate,
        int pairs,
        int pairedWins,
        long medianPssDeltaKb,
        long medianPrivateDirtyDeltaKb,
        long medianMemoryCurrentDeltaBytes,
        long medianHeapPssDeltaKb,
        long medianAnonymousRwDeltaKb,
        long medianAnonymousExecutableDeltaKb,
        Verdict verdict,
        List<PairResult> pairResults
    ) {
        public ConfirmationReport {
            pairResults = pairResults == null ? List.of() : List.copyOf(pairResults);
        }
    }

    public record VarianceClassification(
        List<VarianceCategory> categories,
        List<String> reasons
    ) {
        public VarianceClassification {
            categories = categories == null ? List.of() : List.copyOf(categories);
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public record EvidenceValidationReport(
        int runs,
        int validRuns,
        int invalidRuns,
        List<RunEvidence> runEvidence
    ) {
        public EvidenceValidationReport {
            runEvidence = runEvidence == null ? List.of() : List.copyOf(runEvidence);
        }
    }

    public record EvidenceAnalysisReport(
        String metadataVersion,
        String generatedAt,
        EvidenceConfig config,
        EvidenceValidationReport validation,
        ConfirmationReport confirmation,
        VarianceClassification variance,
        List<PerturbationReport> perturbations,
        Verdict verdict,
        String nextRecommendedAction
    ) {
        public EvidenceAnalysisReport {
            perturbations = perturbations == null ? List.of() : List.copyOf(perturbations);
        }
    }
}
