package com.yourorg.jmoa.plugin.generated.relevance;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;
import com.yourorg.jmoa.plugin.generated.GeneratedClassInventory;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRecord;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeAttribution;
import com.yourorg.jmoa.plugin.generated.GeneratedClassFamilyRuntimeAttribution;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeClassRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** V2-T/V2-U evidence join. It reports mismatches; it never substitutes a historical capture. */
public final class GeneratedFamilyMatchedEvidenceAnalyzer {

    public record EvidenceIdentity(
        String service,
        String artifactSha256,
        String launchMode,
        String runtimePolicy,
        String reducerEngine,
        String familyRegistryVersion,
        String scannerVersion
    ) {
        public EvidenceIdentity {
            service = valueOr(service, "");
            artifactSha256 = valueOr(artifactSha256, "");
            launchMode = valueOr(launchMode, "");
            runtimePolicy = valueOr(runtimePolicy, "");
            reducerEngine = valueOr(reducerEngine, "");
            familyRegistryVersion = valueOr(familyRegistryVersion, "");
            scannerVersion = valueOr(scannerVersion, "");
        }

        static EvidenceIdentity legacy(String service, String artifactSha256) {
            return new EvidenceIdentity(service, artifactSha256, "LEGACY", "LEGACY", "LEGACY", "LEGACY", "LEGACY");
        }
    }

    public record IdentityCheck(
        String field,
        String staticValue,
        String captureValue,
        boolean present,
        boolean matched
    ) { }

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
        int packagedClasses,
        int startupLoaded,
        int warmupLoaded,
        int workloadLoaded,
        int newlyLoadedDuringWarmup,
        int newlyLoadedDuringWorkload,
        int runtimeOnlyStartup,
        int runtimeOnlyWarmup,
        int runtimeOnlyWorkload,
        int histogramPersistentClasses,
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
        boolean identityTupleMatch,
        String evidenceStatus,
        EvidenceIdentity staticIdentity,
        EvidenceIdentity captureIdentity,
        List<IdentityCheck> identityChecks,
        List<FamilyCensus> exclusivePrimaryFamilyCensus,
        int uniqueGeneratedClasses,
        long uniqueGeneratedClassfileBytes,
        int overlappingClassificationClasses,
        List<Lifecycle> lifecycle,
        boolean prototypeAdmitted,
        List<String> boundaries
    ) {
        public Report {
            staticIdentity = staticIdentity == null ? EvidenceIdentity.legacy(service, staticArtifactSha256) : staticIdentity;
            captureIdentity = captureIdentity == null ? EvidenceIdentity.legacy(service, captureArtifactSha256) : captureIdentity;
            identityChecks = identityChecks == null ? List.of() : List.copyOf(identityChecks);
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
        return analyze(EvidenceIdentity.legacy(service, staticArtifactSha256),
            EvidenceIdentity.legacy(service, captureArtifactSha256),
            inventory, startup, warmup, workload, false);
    }

    public Report analyze(
        EvidenceIdentity staticIdentity,
        EvidenceIdentity captureIdentity,
        GeneratedClassInventory inventory,
        GeneratedClassRuntimeAttribution startup,
        GeneratedClassRuntimeAttribution warmup,
        GeneratedClassRuntimeAttribution workload
    ) {
        return analyze(staticIdentity, captureIdentity, inventory, startup, warmup, workload, true);
    }

    Report analyze(
        EvidenceIdentity staticIdentity,
        EvidenceIdentity captureIdentity,
        GeneratedClassInventory inventory,
        GeneratedClassRuntimeAttribution startup,
        GeneratedClassRuntimeAttribution warmup,
        GeneratedClassRuntimeAttribution workload,
        boolean requireCompleteIdentity
    ) {
        staticIdentity = staticIdentity == null ? EvidenceIdentity.legacy("unknown", "") : staticIdentity;
        captureIdentity = captureIdentity == null ? EvidenceIdentity.legacy("unknown", "") : captureIdentity;
        boolean fingerprintsPresent = nonBlank(staticIdentity.artifactSha256()) && nonBlank(captureIdentity.artifactSha256());
        boolean fingerprintMatch = fingerprintsPresent && staticIdentity.artifactSha256().equalsIgnoreCase(captureIdentity.artifactSha256());
        List<IdentityCheck> checks = identityChecks(staticIdentity, captureIdentity);
        boolean tupleFieldsComplete = checks.stream().allMatch(IdentityCheck::present);
        boolean tupleMatch = checks.stream().allMatch(check -> check.present() && check.matched());
        boolean lifecycleComplete = startup != null && warmup != null && workload != null;
        String status = !fingerprintsPresent ? "ARTIFACT_FINGERPRINT_MISSING"
            : !fingerprintMatch ? "ARTIFACT_FINGERPRINT_MISMATCH"
            : requireCompleteIdentity && !tupleFieldsComplete ? "IDENTITY_FIELD_MISSING"
            : !fieldMatched(checks, "service") ? "SERVICE_MISMATCH"
            : !fieldMatched(checks, "launchMode") || !fieldMatched(checks, "runtimePolicy") ? "RUNTIME_SCOPE_MISMATCH"
            : !fieldMatched(checks, "reducerEngine") ? "REDUCER_ENGINE_MISMATCH"
            : !fieldMatched(checks, "familyRegistryVersion") ? "REGISTRY_VERSION_MISMATCH"
            : !fieldMatched(checks, "scannerVersion") ? "SCANNER_VERSION_MISMATCH"
            : !lifecycleComplete ? "LIFECYCLE_CAPTURE_INCOMPLETE"
            : "MATCHED_DIAGNOSTIC_EVIDENCE";
        Map<GeneratedClassFamily, MutableCensus> census = census(inventory);
        return new Report(
            "v2u-generated-family-matched-evidence",
            Instant.now().toString(),
            nonBlank(staticIdentity.service()) ? staticIdentity.service() : "unknown",
            staticIdentity.artifactSha256(), captureIdentity.artifactSha256(), fingerprintMatch, tupleMatch, status,
            staticIdentity, captureIdentity, checks,
            census.entrySet().stream().map(entry -> entry.getValue().toRecord(entry.getKey()))
                .sorted(Comparator.comparing(FamilyCensus::primaryFamily)).toList(),
            census.values().stream().mapToInt(item -> item.uniqueClasses).sum(),
            census.values().stream().mapToLong(item -> item.uniqueBytes).sum(),
            census.values().stream().mapToInt(item -> item.overlaps).sum(),
            lifecycle(census, startup, warmup, workload),
            false,
            List.of(
                "V2-U is diagnostic/report-only and never rewrites generated classes.",
                "Static and runtime evidence are reconciled only when the full identity tuple matches.",
                "Lifecycle captures are diagnostic and are not V2-C claimable memory-pair evidence.",
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
            Set<String> startupClasses = loadedClasses(startup, family);
            Set<String> warmupClasses = loadedClasses(warmup, family);
            Set<String> workloadClasses = loadedClasses(workload, family);
            int warmupNew = differenceCount(warmupClasses, startupClasses);
            int workloadNew = differenceCount(workloadClasses, warmupClasses);
            int workLoaded = count(work);
            int runtimeOnlyStartup = start == null ? 0 : start.runtimeOnlyLoadedCount();
            int runtimeOnlyWarmup = warm == null ? 0 : warm.runtimeOnlyLoadedCount();
            int runtimeOnlyWorkload = work == null ? 0 : work.runtimeOnlyLoadedCount();
            int histogramPersistent = work == null ? 0 : work.histogramClassCount();
            String classification = classification(census.get(family), start, warm, work, warmupNew, workloadNew);
            rows.add(new Lifecycle(family, census.containsKey(family) ? census.get(family).uniqueClasses : 0,
                count(start), count(warm), workLoaded, warmupNew, workloadNew,
                runtimeOnlyStartup, runtimeOnlyWarmup, runtimeOnlyWorkload, histogramPersistent,
                work == null ? 0 : work.histogramBytes(), classification));
        }
        return rows.stream().sorted(Comparator.comparing(Lifecycle::family)).toList();
    }

    private static Map<GeneratedClassFamily, GeneratedClassFamilyRuntimeAttribution> familyMap(GeneratedClassRuntimeAttribution attribution) {
        Map<GeneratedClassFamily, GeneratedClassFamilyRuntimeAttribution> result = new EnumMap<>(GeneratedClassFamily.class);
        if (attribution != null) attribution.families().forEach(item -> result.put(item.family(), item));
        return result;
    }

    private static List<IdentityCheck> identityChecks(EvidenceIdentity staticIdentity, EvidenceIdentity captureIdentity) {
        return List.of(
            check("service", staticIdentity.service(), captureIdentity.service()),
            check("artifactSha256", staticIdentity.artifactSha256(), captureIdentity.artifactSha256()),
            check("launchMode", staticIdentity.launchMode(), captureIdentity.launchMode()),
            check("runtimePolicy", staticIdentity.runtimePolicy(), captureIdentity.runtimePolicy()),
            check("reducerEngine", staticIdentity.reducerEngine(), captureIdentity.reducerEngine()),
            check("familyRegistryVersion", staticIdentity.familyRegistryVersion(), captureIdentity.familyRegistryVersion()),
            check("scannerVersion", staticIdentity.scannerVersion(), captureIdentity.scannerVersion())
        );
    }

    private static IdentityCheck check(String field, String staticValue, String captureValue) {
        boolean present = nonBlank(staticValue) && nonBlank(captureValue);
        return new IdentityCheck(field, staticValue, captureValue, present,
            present && staticValue.equalsIgnoreCase(captureValue));
    }

    private static boolean fieldMatched(List<IdentityCheck> checks, String field) {
        return checks.stream()
            .filter(check -> check.field().equals(field))
            .findFirst()
            .map(check -> check.present() && check.matched())
            .orElse(false);
    }

    private static Set<String> loadedClasses(GeneratedClassRuntimeAttribution attribution, GeneratedClassFamily family) {
        Set<String> result = new HashSet<>();
        if (attribution == null) return result;
        for (GeneratedClassRuntimeClassRecord record : attribution.classes()) {
            if (record.family() == family && record.runtimeLoaded()) {
                result.add(record.className());
            }
        }
        return result;
    }

    private static int differenceCount(Set<String> current, Set<String> previous) {
        int count = 0;
        for (String value : current) {
            if (!previous.contains(value)) count++;
        }
        return count;
    }

    private static String classification(
        MutableCensus census,
        GeneratedClassFamilyRuntimeAttribution startup,
        GeneratedClassFamilyRuntimeAttribution warmup,
        GeneratedClassFamilyRuntimeAttribution workload,
        int newlyLoadedWarmup,
        int newlyLoadedWorkload
    ) {
        if (startup == null && warmup == null && workload == null) {
            return census == null ? "RUNTIME_RELEVANCE_UNKNOWN" : "PACKAGED_NEVER_OBSERVED_IN_CAPTURE";
        }
        if (workload != null && workload.runtimeOnlyLoadedCount() > runtimeOnly(warmup)) {
            return "RUNTIME_GENERATED_WORKLOAD";
        }
        if (warmup != null && warmup.runtimeOnlyLoadedCount() > runtimeOnly(startup)) {
            return "RUNTIME_GENERATED_WARMUP";
        }
        if (startup != null && startup.runtimeOnlyLoadedCount() > 0) {
            return "RUNTIME_GENERATED_STARTUP";
        }
        if (workload != null && workload.histogramClassCount() > 0) {
            return "HISTOGRAM_PERSISTENT";
        }
        if (newlyLoadedWorkload > 0) {
            return "PACKAGED_WORKLOAD_LOADED";
        }
        if (newlyLoadedWarmup > 0) {
            return "PACKAGED_WARMUP_LOADED";
        }
        if (count(startup) > 0) {
            return "PACKAGED_STARTUP_LOADED";
        }
        return census == null ? "RUNTIME_RELEVANCE_UNKNOWN" : "PACKAGED_NEVER_OBSERVED_IN_CAPTURE";
    }

    private static int runtimeOnly(GeneratedClassFamilyRuntimeAttribution value) {
        return value == null ? 0 : value.runtimeOnlyLoadedCount();
    }

    private static int count(GeneratedClassFamilyRuntimeAttribution value) { return value == null ? 0 : value.runtimeLoadedCount(); }
    private static boolean nonBlank(String value) { return value != null && !value.isBlank(); }
    private static String valueOr(String value, String fallback) { return value == null ? fallback : value.trim(); }

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
