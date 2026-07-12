package com.yourorg.jmoa.plugin.generated.relevance;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;
import com.yourorg.jmoa.plugin.generated.GeneratedClassInventory;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRecord;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeAttribution;
import com.yourorg.jmoa.plugin.generated.GeneratedClassFamilyRuntimeAttribution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** V2-T evidence join. It reports mismatches; it never substitutes a historical capture. */
public final class GeneratedFamilyMatchedEvidenceAnalyzer {

    public record FamilyCensus(
        GeneratedClassFamily primaryFamily,
        int uniqueClasses,
        long uniqueClassfileBytes,
        int overlappingClassificationClasses,
        int syntheticMarkedClasses,
        int bridgeMarkedClasses,
        int lambdaRelatedClasses
    ) { }

    public record Lifecycle(
        GeneratedClassFamily family,
        int startupLoaded,
        int warmupLoaded,
        int workloadLoaded,
        int runtimeOnlyClasses,
        long workloadHistogramBytes,
        String classification
    ) { }

    public record Report(
        String metadataVersion,
        String generatedAt,
        String service,
        String staticArtifactSha256,
        String captureArtifactSha256,
        boolean artifactFingerprintMatch,
        String evidenceStatus,
        List<FamilyCensus> exclusivePrimaryFamilyCensus,
        int uniqueGeneratedClasses,
        long uniqueGeneratedClassfileBytes,
        int overlappingClassificationClasses,
        List<Lifecycle> lifecycle,
        boolean prototypeAdmitted,
        List<String> boundaries
    ) {
        public Report {
            exclusivePrimaryFamilyCensus = exclusivePrimaryFamilyCensus == null ? List.of() : List.copyOf(exclusivePrimaryFamilyCensus);
            lifecycle = lifecycle == null ? List.of() : List.copyOf(lifecycle);
            boundaries = boundaries == null ? List.of() : List.copyOf(boundaries);
        }
    }

    public Report analyze(
        String service,
        String staticArtifactSha256,
        String captureArtifactSha256,
        GeneratedClassInventory inventory,
        GeneratedClassRuntimeAttribution startup,
        GeneratedClassRuntimeAttribution warmup,
        GeneratedClassRuntimeAttribution workload
    ) {
        boolean fingerprintsPresent = nonBlank(staticArtifactSha256) && nonBlank(captureArtifactSha256);
        boolean fingerprintMatch = fingerprintsPresent && staticArtifactSha256.equalsIgnoreCase(captureArtifactSha256);
        boolean lifecycleComplete = startup != null && warmup != null && workload != null;
        String status = !fingerprintsPresent ? "ARTIFACT_FINGERPRINT_MISSING"
            : !fingerprintMatch ? "ARTIFACT_FINGERPRINT_MISMATCH"
            : !lifecycleComplete ? "LIFECYCLE_CAPTURE_INCOMPLETE"
            : "MATCHED_DIAGNOSTIC_EVIDENCE";
        Map<GeneratedClassFamily, MutableCensus> census = census(inventory);
        return new Report(
            "v2t-generated-family-matched-evidence",
            Instant.now().toString(),
            nonBlank(service) ? service : "unknown",
            staticArtifactSha256, captureArtifactSha256, fingerprintMatch, status,
            census.entrySet().stream().map(entry -> entry.getValue().toRecord(entry.getKey()))
                .sorted(Comparator.comparing(FamilyCensus::primaryFamily)).toList(),
            census.values().stream().mapToInt(item -> item.uniqueClasses).sum(),
            census.values().stream().mapToLong(item -> item.uniqueBytes).sum(),
            census.values().stream().mapToInt(item -> item.overlaps).sum(),
            lifecycle(census, startup, warmup, workload),
            false,
            List.of(
                "V2-T is diagnostic/report-only and never rewrites generated classes.",
                "Static and runtime evidence are reconciled only when artifact fingerprints match.",
                "A lifecycle capture is not V2-C claimable memory-pair evidence.",
                "prototypeAdmitted remains false until a separate conjunctive semantic and evidence gate passes."
            )
        );
    }

    private static Map<GeneratedClassFamily, MutableCensus> census(GeneratedClassInventory inventory) {
        Map<GeneratedClassFamily, MutableCensus> values = new EnumMap<>(GeneratedClassFamily.class);
        if (inventory == null) return values;
        for (GeneratedClassRecord record : inventory.classes()) {
            if (!record.generatedLike()) continue;
            MutableCensus item = values.computeIfAbsent(record.family(), ignored -> new MutableCensus());
            item.uniqueClasses++;
            item.uniqueBytes += record.classFileBytes();
            boolean synthetic = record.syntheticMethodCount() > 0 || record.classFlags().contains("ACC_SYNTHETIC");
            boolean bridge = record.bridgeMethodCount() > 0;
            boolean lambda = record.lambdaMethodCount() > 0 || record.family() == GeneratedClassFamily.LAMBDA_METAFATORY_SITE;
            if (synthetic) item.synthetic++;
            if (bridge) item.bridge++;
            if (lambda) item.lambda++;
            if ((synthetic ? 1 : 0) + (bridge ? 1 : 0) + (lambda ? 1 : 0) > 1) item.overlaps++;
        }
        return values;
    }

    private static List<Lifecycle> lifecycle(
        Map<GeneratedClassFamily, MutableCensus> census,
        GeneratedClassRuntimeAttribution startup,
        GeneratedClassRuntimeAttribution warmup,
        GeneratedClassRuntimeAttribution workload
    ) {
        Map<GeneratedClassFamily, GeneratedClassFamilyRuntimeAttribution> startupMap = familyMap(startup);
        Map<GeneratedClassFamily, GeneratedClassFamilyRuntimeAttribution> warmupMap = familyMap(warmup);
        Map<GeneratedClassFamily, GeneratedClassFamilyRuntimeAttribution> workloadMap = familyMap(workload);
        List<Lifecycle> rows = new ArrayList<>();
        for (GeneratedClassFamily family : GeneratedClassFamily.values()) {
            if (!census.containsKey(family) && !startupMap.containsKey(family) && !warmupMap.containsKey(family) && !workloadMap.containsKey(family)) continue;
            GeneratedClassFamilyRuntimeAttribution start = startupMap.get(family);
            GeneratedClassFamilyRuntimeAttribution warm = warmupMap.get(family);
            GeneratedClassFamilyRuntimeAttribution work = workloadMap.get(family);
            int workLoaded = count(work);
            int runtimeOnly = work == null ? 0 : work.runtimeOnlyLoadedCount();
            String classification = work == null ? "RUNTIME_RELEVANCE_UNKNOWN"
                : runtimeOnly > 0 ? "RUNTIME_GENERATED_WORKLOAD"
                : workLoaded > 0 ? "PACKAGED_LOADED_WORKLOAD"
                : "STATIC_ONLY_IN_CAPTURE";
            rows.add(new Lifecycle(family, count(start), count(warm), workLoaded, runtimeOnly,
                work == null ? 0 : work.histogramBytes(), classification));
        }
        return rows.stream().sorted(Comparator.comparing(Lifecycle::family)).toList();
    }

    private static Map<GeneratedClassFamily, GeneratedClassFamilyRuntimeAttribution> familyMap(GeneratedClassRuntimeAttribution attribution) {
        Map<GeneratedClassFamily, GeneratedClassFamilyRuntimeAttribution> result = new EnumMap<>(GeneratedClassFamily.class);
        if (attribution != null) attribution.families().forEach(item -> result.put(item.family(), item));
        return result;
    }

    private static int count(GeneratedClassFamilyRuntimeAttribution value) { return value == null ? 0 : value.runtimeLoadedCount(); }
    private static boolean nonBlank(String value) { return value != null && !value.isBlank(); }

    private static final class MutableCensus {
        int uniqueClasses;
        long uniqueBytes;
        int overlaps;
        int synthetic;
        int bridge;
        int lambda;
        FamilyCensus toRecord(GeneratedClassFamily family) {
            return new FamilyCensus(family, uniqueClasses, uniqueBytes, overlaps, synthetic, bridge, lambda);
        }
    }
}
