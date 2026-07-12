package com.yourorg.jmoa.plugin.generated.relevance;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;
import com.yourorg.jmoa.plugin.generated.GeneratedClassFamilyRuntimeAttribution;
import com.yourorg.jmoa.plugin.generated.GeneratedClassInventory;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRecord;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRuntimeAttribution;
import com.yourorg.jmoa.plugin.generated.relevance.GeneratedFamilyRelevanceModels.FamilyCensus;
import com.yourorg.jmoa.plugin.generated.relevance.GeneratedFamilyRelevanceModels.FamilyRoi;
import com.yourorg.jmoa.plugin.generated.relevance.GeneratedFamilyRelevanceModels.FamilyRuntimeReconciliation;
import com.yourorg.jmoa.plugin.generated.relevance.GeneratedFamilyRelevanceModels.FamilySafety;
import com.yourorg.jmoa.plugin.generated.relevance.GeneratedFamilyRelevanceModels.GeneratedFamilyRelevanceReport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Reconciles V2-A's static and diagnostic-only runtime universes. */
public final class GeneratedFamilyRelevanceAnalyzer {

    private static final List<String> BOUNDARIES = List.of(
        "V2-S is report-only; it never rewrites generated classes.",
        "Class-load and histogram inputs are diagnostic evidence and are not V2-C memory-pair evidence.",
        "Runtime relevance raises investigation priority, not mutation permission.",
        "Proxy, CGLIB, ByteBuddy, Hibernate, and Spring AOT families remain mutation-blocked by default."
    );

    private final GeneratedFamilyRegistry registry;

    public GeneratedFamilyRelevanceAnalyzer() {
        this(new GeneratedFamilyRegistry());
    }

    GeneratedFamilyRelevanceAnalyzer(GeneratedFamilyRegistry registry) {
        this.registry = registry;
    }

    public GeneratedFamilyRelevanceReport analyze(
        String service,
        GeneratedClassInventory inventory,
        GeneratedClassRuntimeAttribution runtime
    ) {
        Map<GeneratedClassFamily, FamilyCensus> census = census(inventory);
        Map<GeneratedClassFamily, GeneratedClassFamilyRuntimeAttribution> runtimeFamilies = runtimeFamilies(runtime);
        List<FamilyRuntimeReconciliation> reconciliation = reconciliation(census, runtimeFamilies);
        List<FamilySafety> safety = safety();
        List<FamilyRoi> roi = roi(census, reconciliation, safety);
        return new GeneratedFamilyRelevanceReport(
            "v2s-generated-family-runtime-relevance",
            Instant.now().toString(),
            service == null || service.isBlank() ? "unknown" : service,
            true,
            census.values().stream().sorted(Comparator.comparing(FamilyCensus::family)).toList(),
            reconciliation,
            safety,
            roi,
            false,
            null,
            BOUNDARIES
        );
    }

    private Map<GeneratedClassFamily, FamilyCensus> census(GeneratedClassInventory inventory) {
        Map<GeneratedClassFamily, MutableCensus> values = new EnumMap<>(GeneratedClassFamily.class);
        if (inventory != null) {
            for (GeneratedClassRecord record : inventory.classes()) {
                if (!record.generatedLike()) {
                    continue;
                }
                MutableCensus value = values.computeIfAbsent(record.family(), ignored -> new MutableCensus());
                value.staticClasses++;
                value.classfileBytes += record.classFileBytes();
                value.methods += record.methodCount();
                value.syntheticMethods += record.syntheticMethodCount();
                value.bridgeMethods += record.bridgeMethodCount();
                value.constantPoolEntries += record.constantPoolCount();
                if (record.invokedynamicCount() > 0) {
                    value.bootstrapMethodsClasses++;
                }
            }
        }
        Map<GeneratedClassFamily, FamilyCensus> result = new EnumMap<>(GeneratedClassFamily.class);
        for (Map.Entry<GeneratedClassFamily, MutableCensus> entry : values.entrySet()) {
            MutableCensus value = entry.getValue();
            result.put(entry.getKey(), new FamilyCensus(entry.getKey(), value.staticClasses, value.classfileBytes,
                value.methods, value.syntheticMethods, value.bridgeMethods, value.constantPoolEntries,
                value.bootstrapMethodsClasses, 0, 0, 0,
                "classfile inventory; V2-B attribute detail is not joined in this report"));
        }
        return result;
    }

    private static Map<GeneratedClassFamily, GeneratedClassFamilyRuntimeAttribution> runtimeFamilies(
        GeneratedClassRuntimeAttribution runtime
    ) {
        Map<GeneratedClassFamily, GeneratedClassFamilyRuntimeAttribution> values = new EnumMap<>(GeneratedClassFamily.class);
        if (runtime != null) {
            for (GeneratedClassFamilyRuntimeAttribution family : runtime.families()) {
                values.put(family.family(), family);
            }
        }
        return values;
    }

    private static List<FamilyRuntimeReconciliation> reconciliation(
        Map<GeneratedClassFamily, FamilyCensus> census,
        Map<GeneratedClassFamily, GeneratedClassFamilyRuntimeAttribution> runtimeFamilies
    ) {
        List<FamilyRuntimeReconciliation> result = new ArrayList<>();
        for (GeneratedClassFamily family : GeneratedClassFamily.values()) {
            FamilyCensus staticFamily = census.get(family);
            GeneratedClassFamilyRuntimeAttribution runtimeFamily = runtimeFamilies.get(family);
            int staticClasses = staticFamily == null ? 0 : staticFamily.staticClasses();
            if (staticClasses == 0 && runtimeFamily == null) {
                continue;
            }
            int loaded = runtimeFamily == null ? 0 : runtimeFamily.runtimeLoadedCount();
            int staticAndLoaded = runtimeFamily == null ? 0 : runtimeFamily.staticAndLoadedCount();
            int runtimeOnly = runtimeFamily == null ? 0 : runtimeFamily.runtimeOnlyLoadedCount();
            long instances = runtimeFamily == null ? 0 : runtimeFamily.histogramInstanceCount();
            long bytes = runtimeFamily == null ? 0 : runtimeFamily.histogramBytes();
            String relevance = relevance(staticClasses, loaded, runtimeOnly, bytes, runtimeFamily != null);
            result.add(new FamilyRuntimeReconciliation(family, staticClasses, loaded, staticAndLoaded, runtimeOnly,
                instances, bytes, runtimeFamily == null ? "STATIC_ONLY" : "DIAGNOSTIC_CLASSLOAD_AND_HISTOGRAM",
                runtimeFamily == null ? "not captured" : "see generated-class-origin-map.json", relevance));
        }
        return result.stream().sorted(Comparator.comparing(FamilyRuntimeReconciliation::family)).toList();
    }

    private List<FamilySafety> safety() {
        return registry.definitions().stream().map(definition -> {
            boolean proxy = definition.family() == GeneratedClassFamily.JDK_PROXY
                || definition.family() == GeneratedClassFamily.SPRING_CGLIB
                || definition.family() == GeneratedClassFamily.BYTEBUDDY
                || definition.family() == GeneratedClassFamily.HIBERNATE_PROXY;
            boolean aot = definition.family() == GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS
                || definition.family() == GeneratedClassFamily.SPRING_AOT_REGISTRATION;
            return new FamilySafety(definition.family(), definition.semanticRole(), definition.mutationRisk(),
                definition.defaultAdmissionState().name(), proxy, proxy || aot, proxy || aot,
                definition.requiredProofBeforePrototype());
        }).toList();
    }

    private List<FamilyRoi> roi(
        Map<GeneratedClassFamily, FamilyCensus> census,
        List<FamilyRuntimeReconciliation> reconciliation,
        List<FamilySafety> safety
    ) {
        Map<GeneratedClassFamily, FamilyRuntimeReconciliation> runtime = new EnumMap<>(GeneratedClassFamily.class);
        reconciliation.forEach(value -> runtime.put(value.family(), value));
        Map<GeneratedClassFamily, FamilySafety> safetyByFamily = new EnumMap<>(GeneratedClassFamily.class);
        safety.forEach(value -> safetyByFamily.put(value.family(), value));
        List<FamilyRoi> result = new ArrayList<>();
        for (FamilyRuntimeReconciliation item : reconciliation) {
            FamilyCensus staticFamily = census.get(item.family());
            FamilySafety safetyFamily = safetyByFamily.get(item.family());
            long bytes = staticFamily == null ? 0 : staticFamily.classfileBytes();
            int footprint = bytes >= 256 * 1024L ? 30 : bytes >= 32 * 1024L ? 15 : 5;
            int runtimeScore = "RUNTIME_RELEVANT".equals(item.relevance()) ? 30
                : "LOADED_COLD".equals(item.relevance()) ? 10 : 0;
            int crossService = 0; // Requires multiple service reports; not inferred from one diagnostic capture.
            int risk = "HIGH".equals(safetyFamily.mutationRisk()) ? 40
                : "MEDIUM".equals(safetyFamily.mutationRisk()) ? 20 : 30;
            int complexity = safetyFamily.proxyDispatchSensitive() || safetyFamily.classLoaderSensitive() ? 30 : 10;
            int verification = safetyFamily.reflectionSensitive() ? 20 : 10;
            int total = footprint + runtimeScore + crossService - risk - complexity - verification;
            boolean blocked = "GENERATED_MUTATION_BLOCKED".equals(safetyFamily.defaultAdmissionState());
            String recommendation = blocked ? "GENERATED_MUTATION_BLOCKED" : "GENERATED_REPORT_ONLY";
            result.add(new FamilyRoi(item.family(), bytes, item.staticClasses(), item.runtimeLoadedClasses(),
                item.histogramBytes(), item.relevance(), safetyFamily.defaultAdmissionState(), footprint, runtimeScore,
                crossService, risk, complexity, verification, total, recommendation,
                List.of("Score components are published; V2-S does not infer cross-service presence or mutation safety.")));
        }
        return result.stream().sorted(Comparator.comparingInt(FamilyRoi::totalScore).reversed().thenComparing(FamilyRoi::family)).toList();
    }

    private static String relevance(int staticClasses, int loaded, int runtimeOnly, long histogramBytes, boolean captured) {
        if (!captured) {
            return "RUNTIME_RELEVANCE_UNKNOWN";
        }
        if (loaded == 0 && runtimeOnly == 0) {
            return staticClasses > 0 ? "STATIC_ONLY" : "RUNTIME_RELEVANCE_UNKNOWN";
        }
        if (runtimeOnly > 0) {
            return "RUNTIME_GENERATED";
        }
        if (histogramBytes > 0 || loaded >= Math.max(1, staticClasses / 2)) {
            return "RUNTIME_RELEVANT";
        }
        return "LOADED_COLD";
    }

    private static final class MutableCensus {
        int staticClasses;
        long classfileBytes;
        int methods;
        int syntheticMethods;
        int bridgeMethods;
        long constantPoolEntries;
        int bootstrapMethodsClasses;
    }
}
