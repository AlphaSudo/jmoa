package com.yourorg.jmoa.plugin.attribution;

import com.yourorg.jmoa.plugin.evidence.EvidenceModels.Verdict;

import java.io.File;
import java.util.List;

public final class AttributionModels {

    private AttributionModels() {
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum CausalHypothesisType {
        CLASS_COUNT_SAVINGS,
        GENERATED_CLASS_SAVINGS,
        METASPACE_SAVINGS,
        HEAP_PAGE_TOUCH_REDUCTION,
        HEAP_PAGE_TOUCH_GROWTH,
        RETAINED_OBJECT_REDUCTION,
        RETAINED_OBJECT_GROWTH,
        ANONYMOUS_RW_ALLOCATOR_REDUCTION,
        ANONYMOUS_RW_ALLOCATOR_GROWTH,
        CODECACHE_REDUCTION,
        LAUNCH_MODE_MATERIALIZATION_EFFECT,
        BYTECODE_SURFACE_REDUCTION,
        DIAGNOSTIC_PERTURBATION,
        UNKNOWN
    }

    public record AttributionConfig(
        boolean requireV2cValid,
        boolean includeJfr,
        boolean includeAsyncProfiler,
        boolean includeJol,
        boolean diagnosticOnly,
        File generatedClassReport,
        File bytecodeRuntimeCorrelationReport
    ) {
        public static AttributionConfig defaults() {
            return new AttributionConfig(true, false, false, false, true, null, null);
        }
    }

    public record MemoryCategoryDelta(
        String category,
        String unit,
        long medianDelta,
        List<Long> pairDeltas
    ) {
        public MemoryCategoryDelta {
            pairDeltas = pairDeltas == null ? List.of() : List.copyOf(pairDeltas);
        }
    }

    public record SmapsNmtReconciliation(
        long medianPssDeltaKb,
        long medianPrivateDirtyDeltaKb,
        long medianNmtTotalCommittedDeltaKb,
        long nmtToPssGapKb,
        String classification,
        List<String> reasons
    ) {
        public SmapsNmtReconciliation {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public record ObjectFamilyDelta(
        String family,
        long medianInstanceDelta,
        long medianByteDelta
    ) {
    }

    public record HeapObjectAttribution(
        long medianHeapPssDeltaKb,
        long medianHeapPrivateDirtyDeltaKb,
        long medianHeapUsedDeltaKb,
        long medianHeapCommittedDeltaKb,
        long medianClassHistogramBytesDelta,
        String classification,
        List<ObjectFamilyDelta> familyDeltas
    ) {
        public HeapObjectAttribution {
            familyDeltas = familyDeltas == null ? List.of() : List.copyOf(familyDeltas);
        }
    }

    public record ClassMetaspaceAttribution(
        long medianClassHistogramClassCountDelta,
        long medianMetaspaceCommittedDeltaKb,
        long medianClassCommittedDeltaKb,
        long medianCodeCommittedDeltaKb,
        String interpretation
    ) {
    }

    public record GeneratedFamilySummary(
        String family,
        long staticClassCount,
        long generatedLikeClassCount,
        long classfileBytes,
        long runtimeLoadedClassCount,
        long workloadSurvivorClassCount,
        long histogramBytes
    ) {
    }

    public record GeneratedFamilyAttribution(
        boolean present,
        String metadataVersion,
        long totalClassesScanned,
        long generatedLikeClasses,
        List<GeneratedFamilySummary> families,
        String interpretation
    ) {
        public GeneratedFamilyAttribution {
            families = families == null ? List.of() : List.copyOf(families);
        }
    }

    public record BytecodeRuntimeAttribution(
        boolean present,
        String metadataVersion,
        long totalProfileClasses,
        long totalRuntimeLoadedClasses,
        long profileClassesObservedLoaded,
        long profileClassesWithHistogramInstances,
        long near64kMethods,
        long near64kRuntimeLoadedMethods,
        List<GeneratedFamilySummary> families,
        String interpretation
    ) {
        public BytecodeRuntimeAttribution {
            families = families == null ? List.of() : List.copyOf(families);
        }
    }

    public record CausalHypothesis(
        CausalHypothesisType hypothesis,
        Confidence confidence,
        List<String> evidence,
        List<String> notEvidence,
        String nextAction
    ) {
        public CausalHypothesis {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            notEvidence = notEvidence == null ? List.of() : List.copyOf(notEvidence);
        }
    }

    public record MemoryAttributionReport(
        String metadataVersion,
        String generatedAt,
        String evidenceVerdictSource,
        Verdict evidenceVerdict,
        boolean v2cValid,
        String service,
        String phase,
        List<MemoryCategoryDelta> categoryDeltas,
        SmapsNmtReconciliation smapsNmtReconciliation,
        HeapObjectAttribution heapObjectAttribution,
        ClassMetaspaceAttribution classMetaspaceAttribution,
        GeneratedFamilyAttribution generatedFamilyAttribution,
        BytecodeRuntimeAttribution bytecodeRuntimeAttribution,
        List<CausalHypothesis> causalHypotheses,
        List<String> boundaries
    ) {
        public MemoryAttributionReport {
            categoryDeltas = categoryDeltas == null ? List.of() : List.copyOf(categoryDeltas);
            causalHypotheses = causalHypotheses == null ? List.of() : List.copyOf(causalHypotheses);
            boundaries = boundaries == null ? List.of() : List.copyOf(boundaries);
        }
    }
}
