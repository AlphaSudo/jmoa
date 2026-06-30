package com.yourorg.jmoa.plugin.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yourorg.jmoa.plugin.dedup.SharedGroup;
import com.yourorg.jmoa.plugin.deps.DependencyExpansionResult;
import com.yourorg.jmoa.plugin.filter.ExclusionReason;
import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyReason;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import com.yourorg.jmoa.plugin.modec.HybridPackagingSummary;
import com.yourorg.jmoa.plugin.modec.OptimizedDependencyJarPackagingResult;
import com.yourorg.jmoa.plugin.runtime.GeneratedRuntimeArtifact;
import com.yourorg.jmoa.plugin.runtime.Tier1RuntimePlan;
import com.yourorg.jmoa.plugin.runtime.Tier1RuntimePlanResult;
import com.yourorg.jmoa.plugin.runtime.Tier2AdapterArtifact;
import com.yourorg.jmoa.plugin.scanner.LambdaSite;
import com.yourorg.jmoa.plugin.scanner.ScanResult;
import com.yourorg.jmoa.plugin.weave.LambdaWeaveSanitySummary;
import com.yourorg.jmoa.plugin.weave.WeaveExecutionResult;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

public final class DeduplicationReport {

    public static void writeReport(
            File reportFile,
            int classesScanned,
            ScanResult scanResult,
            int skippedPrivateAccess,
            int syntheticMethodsWidened,
            List<SharedGroup> groups,
            LambdaFilterResult filterResult,
            Tier1RuntimePlanResult runtimePlanResult,
            GeneratedRuntimeArtifact generatedRuntimeArtifact,
            List<Tier2AdapterArtifact> tier2Artifacts,
            WeaveExecutionResult weaveExecutionResult,
            String executionMode,
            List<String> classRootsScanned,
            DependencyExpansionResult dependencyExpansionResult,
            OptimizedDependencyJarPackagingResult optimizedJarPackagingResult) throws IOException {

        writeReport(
            reportFile,
            classesScanned,
            scanResult,
            skippedPrivateAccess,
            syntheticMethodsWidened,
            groups,
            filterResult,
            runtimePlanResult,
            generatedRuntimeArtifact,
            tier2Artifacts,
            weaveExecutionResult,
            executionMode,
            classRootsScanned,
            dependencyExpansionResult,
            optimizedJarPackagingResult,
            ObservedAdmissionReportContext.disabled()
        );
    }

    public static void writeReport(
            File reportFile,
            int classesScanned,
            ScanResult scanResult,
            int skippedPrivateAccess,
            int syntheticMethodsWidened,
            List<SharedGroup> groups,
            LambdaFilterResult filterResult,
            Tier1RuntimePlanResult runtimePlanResult,
            GeneratedRuntimeArtifact generatedRuntimeArtifact,
            List<Tier2AdapterArtifact> tier2Artifacts,
            WeaveExecutionResult weaveExecutionResult,
            String executionMode,
            List<String> classRootsScanned,
            DependencyExpansionResult dependencyExpansionResult,
            OptimizedDependencyJarPackagingResult optimizedJarPackagingResult,
            ObservedAdmissionReportContext observedAdmissionContext) throws IOException {

        writeReport(
            reportFile,
            classesScanned,
            scanResult.totalLambdaSites(),
            scanResult.skippedCapturing(),
            scanResult.skippedSerializable(),
            skippedPrivateAccess,
            syntheticMethodsWidened,
            groups,
            scanResult.metadata(),
            filterResult,
            runtimePlanResult,
            generatedRuntimeArtifact,
            tier2Artifacts,
            weaveExecutionResult,
            executionMode,
            classRootsScanned,
            dependencyExpansionResult,
            optimizedJarPackagingResult,
            observedAdmissionContext
        );
    }

    public static void writeReport(
            File reportFile,
            int classesScanned,
            int totalLambdaSites,
            int skippedCapturing,
            int skippedSerializable,
            int skippedPrivateAccess,
            int syntheticMethodsWidened,
            List<SharedGroup> groups) throws IOException {
        writeReport(
            reportFile,
            classesScanned,
            totalLambdaSites,
            skippedCapturing,
            skippedSerializable,
            skippedPrivateAccess,
            syntheticMethodsWidened,
            groups,
            List.of(),
            null,
            null,
            null,
            List.of(),
            null,
            null,
            List.of(),
            DependencyExpansionResult.disabled(null),
            OptimizedDependencyJarPackagingResult.disabled(null),
            ObservedAdmissionReportContext.disabled()
        );
    }

    public static void writeReport(
            File reportFile,
            int classesScanned,
            int totalLambdaSites,
            int skippedCapturing,
            int skippedSerializable,
            int skippedPrivateAccess,
            int syntheticMethodsWidened,
            List<SharedGroup> groups,
            List<LambdaMeta> metadata,
            LambdaFilterResult filterResult,
            Tier1RuntimePlanResult runtimePlanResult,
            List<Tier2AdapterArtifact> tier2Artifacts,
            WeaveExecutionResult weaveExecutionResult,
            String executionMode,
            List<String> classRootsScanned) throws IOException {
        writeReport(
            reportFile,
            classesScanned,
            totalLambdaSites,
            skippedCapturing,
            skippedSerializable,
            skippedPrivateAccess,
            syntheticMethodsWidened,
            groups,
            metadata,
            filterResult,
            runtimePlanResult,
            null,
            tier2Artifacts,
            weaveExecutionResult,
            executionMode,
            classRootsScanned,
            DependencyExpansionResult.disabled(null),
            OptimizedDependencyJarPackagingResult.disabled(null),
            ObservedAdmissionReportContext.disabled()
        );
    }

    public static void writeReport(
            File reportFile,
            int classesScanned,
            int totalLambdaSites,
            int skippedCapturing,
            int skippedSerializable,
            int skippedPrivateAccess,
            int syntheticMethodsWidened,
            List<SharedGroup> groups,
            List<LambdaMeta> metadata,
            LambdaFilterResult filterResult,
            Tier1RuntimePlanResult runtimePlanResult,
            GeneratedRuntimeArtifact generatedRuntimeArtifact,
            List<Tier2AdapterArtifact> tier2Artifacts,
            WeaveExecutionResult weaveExecutionResult,
            String executionMode,
            List<String> classRootsScanned,
            DependencyExpansionResult dependencyExpansionResult,
            OptimizedDependencyJarPackagingResult optimizedJarPackagingResult) throws IOException {
        writeReport(
            reportFile,
            classesScanned,
            totalLambdaSites,
            skippedCapturing,
            skippedSerializable,
            skippedPrivateAccess,
            syntheticMethodsWidened,
            groups,
            metadata,
            filterResult,
            runtimePlanResult,
            generatedRuntimeArtifact,
            tier2Artifacts,
            weaveExecutionResult,
            executionMode,
            classRootsScanned,
            dependencyExpansionResult,
            optimizedJarPackagingResult,
            ObservedAdmissionReportContext.disabled()
        );
    }

    public static void writeReport(
            File reportFile,
            int classesScanned,
            int totalLambdaSites,
            int skippedCapturing,
            int skippedSerializable,
            int skippedPrivateAccess,
            int syntheticMethodsWidened,
            List<SharedGroup> groups,
            List<LambdaMeta> metadata,
            LambdaFilterResult filterResult,
            Tier1RuntimePlanResult runtimePlanResult,
            GeneratedRuntimeArtifact generatedRuntimeArtifact,
            List<Tier2AdapterArtifact> tier2Artifacts,
            WeaveExecutionResult weaveExecutionResult,
            String executionMode,
            List<String> classRootsScanned,
            DependencyExpansionResult dependencyExpansionResult,
            OptimizedDependencyJarPackagingResult optimizedJarPackagingResult,
            ObservedAdmissionReportContext observedAdmissionContext) throws IOException {

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("timestamp", Instant.now().toString());
        report.put("metadataVersion", "v3.3-alpha");
        report.put("executionMode", executionMode == null ? "" : executionMode);
        report.put("classRootsScanned", classRootsScanned == null ? List.of() : classRootsScanned);
        report.put("classesScanned", classesScanned);
        report.put("totalLambdaSites", totalLambdaSites);
        report.put("statelessCandidates", totalLambdaSites - skippedCapturing - skippedSerializable);
        report.put("skippedCapturing", skippedCapturing);
        report.put("skippedSerializable", skippedSerializable);
        report.put("skippedPrivateAccess", skippedPrivateAccess);
        report.put("syntheticMethodsWidened", syntheticMethodsWidened);
        report.put("deduplicatedGroups", groups.size());
        report.put("generatedClasses", groups.size());

        int eliminatedDuplicates = 0;
        List<Map<String, Object>> topGroups = new ArrayList<>();
        List<Map<String, Object>> affectedClasses = new ArrayList<>();
        Set<String> groupedSiteKeys = new HashSet<>();

        for (SharedGroup group : groups) {
            int sitesSize = group.sites().size();
            eliminatedDuplicates += (sitesSize - 1);

            Map<String, Object> groupInfo = new LinkedHashMap<>();
            String groupKeyDesc = group.key().implOwner() + "::" + group.key().implName() + " → " + group.key().samInterfaceInternalName();
            groupInfo.put("key", groupKeyDesc);
            groupInfo.put("duplicateGroupKey", toDuplicateGroupKey(group.key()));
            groupInfo.put("sites", sitesSize);
            groupInfo.put("saved", ((sitesSize - 1) * 2) + " KB");
            groupInfo.put("accessTier", group.accessTier().name());
            groupInfo.put("accessRationale", group.accessPlan().rationale());
            topGroups.add(groupInfo);

            for (LambdaSite site : group.sites()) {
                groupedSiteKeys.add(site.siteKey());
                Map<String, Object> affected = new LinkedHashMap<>();
                affected.put("className", site.classNode().name.replace('/', '.'));
                affected.put("method", site.methodNode().name);
                affected.put("siteKey", site.siteKey());
                affected.put("lineNumber", -1);
                affected.put("changeType", "INVOKEDYNAMIC → GETSTATIC");
                affected.put("sharedWith", group.synthClassName().substring(group.synthClassName().lastIndexOf('/') + 1));
                affected.put("reason", "Method reference/lambda matching " + groupKeyDesc);
                affected.put("accessTier", group.accessTier().name());
                affected.put("accessRationale", group.accessPlan().rationale());
                affectedClasses.add(affected);
            }

            if (group.needsAccessWidening()) {
                Map<String, Object> affected = new LinkedHashMap<>();
                affected.put("className", group.key().implOwner().replace('/', '.'));
                affected.put("method", group.key().implName());
                affected.put("lineNumber", -1);
                affected.put("changeType", "ACC_PRIVATE → package-private");
                affected.put("sharedWith", null);
                affected.put("reason", "Synthetic method widened for package-scoped sharing");
                affectedClasses.add(affected);
            }
        }

        topGroups.sort((g1, g2) -> Integer.compare((Integer) g2.get("sites"), (Integer) g1.get("sites")));

        report.put("eliminatedDuplicates", eliminatedDuplicates);
        report.put("estimatedMetaspaceSaved", (eliminatedDuplicates * 2) + " KB");
        report.put("topGroups", topGroups);
        report.put("affectedClasses", affectedClasses);
        report.put("metadataSummary", buildMetadataSummary(metadata, groupedSiteKeys));
        report.put("filterSummary", buildFilterSummary(filterResult));
        report.put("observedSiteAdmissionEnabled", observedAdmissionContext != null && observedAdmissionContext.enabled());
        report.put("observedAdmissionSitesFile", observedAdmissionContext == null ? "" : observedAdmissionContext.requestedFile());
        report.put("observedAdmissionSitesResolvedFile", observedAdmissionContext == null ? "" : observedAdmissionContext.resolvedFile());
        report.put("observedAdmissionSitesLoaded", observedAdmissionContext != null && observedAdmissionContext.loaded());
        report.put("observedAdmissionSiteKeyCount", observedAdmissionContext == null ? 0 : observedAdmissionContext.siteKeys().size());
        report.put("observedAdmissionTierMode", observedAdmissionContext == null ? "" : observedAdmissionContext.tierMode());
        report.put("phase21aObservedAdmissionSummary", buildObservedAdmissionSummary(filterResult, observedAdmissionContext));
        report.put("tier1RuntimeSummary", buildTier1RuntimeSummary(runtimePlanResult));
        report.put("tier2AdapterSummary", buildTier2AdapterSummary(tier2Artifacts));
        report.put("weaveSummary", buildWeaveSummary(weaveExecutionResult));
        report.put("replacementCostSummary", buildReplacementCostSummary(
            generatedRuntimeArtifact,
            tier2Artifacts,
            weaveExecutionResult,
            optimizedJarPackagingResult
        ));
        report.put("modeCRewriteSummary", buildModeCRewriteSummary(executionMode, filterResult, weaveExecutionResult));
        report.put("dependencyExpansionSummary", buildDependencyExpansionSummary(dependencyExpansionResult));
        report.put("optimizedDependencyJarSummary", buildOptimizedDependencyJarSummary(optimizedJarPackagingResult));
        report.put("hybridPackagingSummary", buildHybridPackagingSummary(
            optimizedJarPackagingResult == null ? null : optimizedJarPackagingResult.hybridPackagingSummary()
        ));

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(reportFile, report);
    }

    private static Map<String, Object> buildMetadataSummary(List<LambdaMeta> metadata, Set<String> groupedSiteKeys) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalMetadataSites", metadata.size());
        summary.put("uniqueSiteKeys", metadata.stream().map(LambdaMeta::siteKey).distinct().count());
        summary.put("statelessCandidateSites", metadata.stream().filter(LambdaMeta::isStatelessCandidate).count());
        summary.put("capturingSites", metadata.stream().filter(LambdaMeta::capturing).count());
        summary.put("serializableSites", metadata.stream().filter(LambdaMeta::serializable).count());
        summary.put("groupedSites", groupedSiteKeys.size());
        summary.put("ungroupedSites", Math.max(0, metadata.size() - groupedSiteKeys.size()));
        summary.put("duplicateGroupKeys", metadata.stream().map(LambdaMeta::duplicateGroupKey).distinct().count());
        return summary;
    }

    private static Map<String, Object> buildFilterSummary(LambdaFilterResult filterResult) {
        if (filterResult == null) {
            return Map.of();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalDecisions", filterResult.decisions().size());
        summary.put("eligibleSites", filterResult.eligible().size());
        summary.put("excludedSites", filterResult.excluded().size());
        summary.put("tier1EligibleSites", filterResult.tier1Eligible().size());
        summary.put("tier2EligibleSites", filterResult.tier2Eligible().size());
        summary.put("observedProfileSites", filterResult.observedSiteCount());
        summary.put("inferredColdSites", filterResult.inferredColdSiteCount());

        Map<String, Object> excludedByRule = new LinkedHashMap<>();
        Map<ExclusionReason, Long> counts = filterResult.exclusionCounts();
        Map<ExclusionReason, Double> percentages = filterResult.exclusionPercentages();
        for (ExclusionReason reason : ExclusionReason.values()) {
            Map<String, Object> ruleSummary = new LinkedHashMap<>();
            ruleSummary.put("count", counts.getOrDefault(reason, 0L));
            ruleSummary.put("percentage", percentages.getOrDefault(reason, 0.0d));
            excludedByRule.put(reason.name(), ruleSummary);
        }
        summary.put("excludedByRule", excludedByRule);
        summary.put("frameworkSafetySummary", buildFrameworkSafetySummary(filterResult));
        summary.put("frameworkDecisions", buildFrameworkDecisions(filterResult));
        return summary;
    }

    private static Map<String, Object> buildObservedAdmissionSummary(
        LambdaFilterResult filterResult,
        ObservedAdmissionReportContext context
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        ObservedAdmissionReportContext safeContext = context == null
            ? ObservedAdmissionReportContext.disabled()
            : context;
        Set<String> loadedSiteKeys = safeContext.siteKeys();

        summary.put("enabled", safeContext.enabled());
        summary.put("requestedSites", loadedSiteKeys.size());
        summary.put("loadedSiteKeys", loadedSiteKeys.size());
        summary.put("observedAdmissionSitesFile", safeContext.requestedFile());
        summary.put("observedAdmissionSitesResolvedFile", safeContext.resolvedFile());
        summary.put("observedAdmissionSitesLoaded", safeContext.loaded());
        summary.put("observedAdmissionTierMode", safeContext.tierMode());

        if (filterResult == null || loadedSiteKeys.isEmpty()) {
            summary.put("matchedSites", 0L);
            summary.put("admittedSites", 0L);
            summary.put("rejectedSites", 0L);
            summary.put("admittedByReasonCount", 0L);
            summary.put("admittedTier1", 0L);
            summary.put("admittedTier2", 0L);
            summary.put("admittedUnknown", 0L);
            summary.put("topRejectedReasons", Map.of());
            summary.put("topAdmittedPackages", Map.of());
            return summary;
        }

        List<com.yourorg.jmoa.plugin.filter.LambdaFilterDecision> matched = filterResult.decisions().stream()
            .filter(decision -> loadedSiteKeys.contains(decision.meta().siteKey()))
            .toList();
        List<com.yourorg.jmoa.plugin.filter.LambdaFilterDecision> admitted = matched.stream()
            .filter(decision -> decision.frameworkSafetyDecision() != null)
            .filter(decision -> decision.frameworkSafetyDecision().allowed())
            .filter(decision -> decision.frameworkSafetyDecision().reasons().contains(FrameworkSafetyReason.OBSERVED_SITE_KEY_ADMITTED))
            .toList();
        List<com.yourorg.jmoa.plugin.filter.LambdaFilterDecision> rejected = matched.stream()
            .filter(decision -> decision.frameworkSafetyDecision() == null || !decision.frameworkSafetyDecision().allowed())
            .toList();

        summary.put("matchedSites", matched.size());
        summary.put("admittedSites", admitted.size());
        summary.put("rejectedSites", rejected.size());
        summary.put("admittedByReasonCount", filterResult.frameworkSafetySummary()
            .byReason()
            .getOrDefault(FrameworkSafetyReason.OBSERVED_SITE_KEY_ADMITTED, 0));
        summary.put("admittedTier1", admitted.stream()
            .filter(decision -> decision.tier() == com.yourorg.jmoa.plugin.filter.LambdaTier.TIER1)
            .count());
        summary.put("admittedTier2", admitted.stream()
            .filter(decision -> decision.tier() == com.yourorg.jmoa.plugin.filter.LambdaTier.TIER2)
            .count());
        summary.put("admittedUnknown", admitted.stream()
            .filter(decision -> decision.tier() == null)
            .count());
        summary.put("topRejectedReasons", countRejectedReasons(rejected));
        summary.put("topAdmittedPackages", countAdmittedPackages(admitted));
        return summary;
    }

    private static Map<String, Long> countRejectedReasons(
        List<com.yourorg.jmoa.plugin.filter.LambdaFilterDecision> rejected
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (var decision : rejected) {
            if (decision.exclusionReason() != null) {
                counts.merge(decision.exclusionReason().name(), 1L, Long::sum);
            }
            if (decision.frameworkSafetyDecision() != null) {
                for (FrameworkSafetyReason reason : decision.frameworkSafetyDecision().reasons()) {
                    counts.merge(reason.name(), 1L, Long::sum);
                }
            }
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(20)
            .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }

    private static Map<String, Long> countAdmittedPackages(
        List<com.yourorg.jmoa.plugin.filter.LambdaFilterDecision> admitted
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (var decision : admitted) {
            counts.merge(packageName(decision.meta().ownerInternalName()), 1L, Long::sum);
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(20)
            .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }

    private static String packageName(String ownerInternalName) {
        int index = ownerInternalName == null ? -1 : ownerInternalName.lastIndexOf('/');
        return index < 0 ? "" : ownerInternalName.substring(0, index).replace('/', '.');
    }

    private static Map<String, Object> buildFrameworkSafetySummary(LambdaFilterResult filterResult) {
        var frameworkSummary = filterResult.frameworkSafetySummary();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("enabled", true);
        summary.put("totalFrameworkSites", frameworkSummary.totalFrameworkSites());
        summary.put("allowedFrameworkSites", frameworkSummary.allowedFrameworkSites());
        summary.put("deniedFrameworkSites", frameworkSummary.deniedFrameworkSites());

        Map<String, Object> byLevel = new LinkedHashMap<>();
        frameworkSummary.byLevel().forEach((level, count) -> byLevel.put(level.name(), count));
        summary.put("byLevel", byLevel);

        Map<String, Object> byReason = new LinkedHashMap<>();
        for (FrameworkSafetyReason reason : FrameworkSafetyReason.values()) {
            byReason.put(reason.name(), frameworkSummary.byReason().getOrDefault(reason, 0));
        }
        summary.put("byReason", byReason);
        return summary;
    }

    private static List<Map<String, Object>> buildFrameworkDecisions(LambdaFilterResult filterResult) {
        List<Map<String, Object>> decisions = new ArrayList<>();
        for (var decision : filterResult.decisions()) {
            if (decision.frameworkSafetyDecision() == null) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("siteKey", decision.meta().siteKey());
            entry.put("ownerClass", decision.meta().ownerInternalName());
            entry.put("level", decision.frameworkSafetyDecision().level().name());
            entry.put("allowed", decision.frameworkSafetyDecision().allowed());
            entry.put("reasons", decision.frameworkSafetyReasons());
            decisions.add(entry);
        }
        return decisions;
    }

    private static Map<String, Object> buildTier1RuntimeSummary(Tier1RuntimePlanResult runtimePlanResult) {
        if (runtimePlanResult == null) {
            return Map.of();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("supportedTier1Sites", runtimePlanResult.supportedPlans().size());
        summary.put("unsupportedTier1Sites", runtimePlanResult.unsupportedTier1Sites().size());

        List<Map<String, Object>> supportedPlans = new ArrayList<>();
        for (Tier1RuntimePlan plan : runtimePlanResult.supportedPlans()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("slotId", plan.slotId());
            entry.put("siteKey", plan.decision().meta().siteKey());
            entry.put("samInterface", plan.adapterKind().samInterfaceInternalName());
            entry.put("factoryMethod", plan.adapterKind().factoryMethodName());
            entry.put("factoryDescriptor", plan.adapterKind().factoryMethodDescriptor());
            supportedPlans.add(entry);
        }
        summary.put("supportedPlans", supportedPlans);

        List<Map<String, Object>> unsupportedSites = new ArrayList<>();
        for (var decision : runtimePlanResult.unsupportedTier1Sites()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("siteKey", decision.meta().siteKey());
            entry.put("samInterface", decision.meta().samInterfaceInternalName());
            unsupportedSites.add(entry);
        }
        summary.put("unsupportedSites", unsupportedSites);
        return summary;
    }

    private static Map<String, Object> buildTier2AdapterSummary(List<Tier2AdapterArtifact> tier2Artifacts) {
        if (tier2Artifacts == null) {
            return Map.of();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedTier2Adapters", tier2Artifacts.size());
        summary.put("generatedTier2AdapterBytes", tier2Artifacts.stream().mapToLong(artifact -> artifact.classBytes().length).sum());

        List<Map<String, Object>> adapters = new ArrayList<>();
        for (Tier2AdapterArtifact artifact : tier2Artifacts) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("internalName", artifact.internalName());
            entry.put("classFile", artifact.internalName() + ".class");
            entry.put("classBytes", artifact.classBytes().length);
            adapters.add(entry);
        }
        summary.put("generatedAdapters", adapters);
        return summary;
    }

    private static Map<String, Object> buildWeaveSummary(WeaveExecutionResult weaveExecutionResult) {
        if (weaveExecutionResult == null) {
            return Map.of();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("failFast", weaveExecutionResult.failFast());
        summary.put("plannedSites", weaveExecutionResult.plannedSites());
        summary.put("targetedClasses", weaveExecutionResult.targetedClasses());
        summary.put("rewrittenClasses", weaveExecutionResult.rewrittenClasses());
        summary.put("failedClasses", weaveExecutionResult.failedClasses());
        summary.put("failedClassNames", weaveExecutionResult.failedClassNames());
        summary.put("sanitySummary", buildWeaveSanitySummary(weaveExecutionResult.sanitySummary()));
        return summary;
    }

    private static Map<String, Object> buildWeaveSanitySummary(LambdaWeaveSanitySummary sanitySummary) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("expectedEligibleSites", sanitySummary.expectedEligibleSites());
        summary.put("verifiedEligibleSites", sanitySummary.verifiedEligibleSites());
        summary.put("rewrittenEligibleSites", sanitySummary.rewrittenEligibleSites());
        summary.put("remainingEligibleSites", sanitySummary.remainingEligibleSites());
        summary.put("unexpectedRemovedSites", sanitySummary.unexpectedRemovedSites());
        summary.put("unverifiedEligibleSites", sanitySummary.unverifiedEligibleSites());
        summary.put("assertionsPassed", sanitySummary.assertionsPassed());
        summary.put("remainingEligibleSiteKeys", sanitySummary.remainingEligibleSiteKeys());
        summary.put("unexpectedRemovedSiteKeys", sanitySummary.unexpectedRemovedSiteKeys());
        return summary;
    }

    private static Map<String, Object> buildDependencyExpansionSummary(DependencyExpansionResult dependencyExpansionResult) {
        if (dependencyExpansionResult == null) {
            return Map.of();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("outputDirectory",
            dependencyExpansionResult.outputDirectory() == null ? "" : dependencyExpansionResult.outputDirectory().getAbsolutePath());
        summary.put("expandedRootCount", dependencyExpansionResult.roots().size());
        summary.put("jarsSeen", dependencyExpansionResult.jarsSeen());
        summary.put("jarsExpanded", dependencyExpansionResult.jarsExpanded());
        summary.put("jarsSkipped", dependencyExpansionResult.jarsSkipped());
        summary.put("totalClassesExpanded", dependencyExpansionResult.totalClassesExpanded());

        List<Map<String, Object>> roots = new ArrayList<>();
        for (var root : dependencyExpansionResult.roots()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("coordinate", root.coordinate().displayName());
            entry.put("originalJar", root.originalJar().getAbsolutePath());
            entry.put("expandedRoot", root.expandedRoot().getAbsolutePath());
            entry.put("classCount", root.classCount());
            roots.add(entry);
        }
        summary.put("roots", roots);
        return summary;
    }

    private static Map<String, Object> buildModeCRewriteSummary(
        String executionMode,
        LambdaFilterResult filterResult,
        WeaveExecutionResult weaveExecutionResult
    ) {
        if (!"MODE_C".equals(executionMode) || filterResult == null || weaveExecutionResult == null) {
            return Map.of();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalSites", filterResult.decisions().size());
        summary.put("eligibleSites", filterResult.eligible().size());
        summary.put("rewrittenSites", weaveExecutionResult.plannedSites());
        summary.put("rewrittenClasses", weaveExecutionResult.rewrittenClasses());
        summary.put("skippedFrameworkSites", filterResult.frameworkSafetySummary().deniedFrameworkSites());
        return summary;
    }

    private static Map<String, Object> buildOptimizedDependencyJarSummary(
        OptimizedDependencyJarPackagingResult packagingResult
    ) {
        if (packagingResult == null) {
            return Map.of();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("outputDirectory",
            packagingResult.outputDirectory() == null ? "" : packagingResult.outputDirectory().getAbsolutePath());
        summary.put("optimizedJarCount", packagingResult.artifacts().size());

        List<Map<String, Object>> artifacts = new ArrayList<>();
        for (var artifact : packagingResult.artifacts()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("coordinate", artifact.coordinate().displayName());
            entry.put("originalJar", artifact.originalJar().getAbsolutePath());
            entry.put("optimizedJar", artifact.optimizedJar().getAbsolutePath());
            entry.put("rewrittenClasses", artifact.rewrittenClasses());
            entry.put("unchangedClasses", artifact.unchangedClasses());
            entry.put("generatedAdapters", artifact.generatedAdapters());
            entry.put("copiedResources", artifact.copiedResources());
            entry.put("removedSignatures", artifact.removedSignatures());
            entry.put("originalJarBytes", artifact.originalJarBytes());
            entry.put("optimizedJarBytes", artifact.optimizedJarBytes());
            entry.put("jarByteDelta", artifact.jarByteDelta());
            artifacts.add(entry);
        }
        summary.put("artifacts", artifacts);
        return summary;
    }

    private static Map<String, Object> buildHybridPackagingSummary(HybridPackagingSummary hybridSummary) {
        HybridPackagingSummary summaryValue = hybridSummary == null ? HybridPackagingSummary.disabled() : hybridSummary;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("enabled", summaryValue.enabled());
        summary.put("hybridOverlayCoordinates", summaryValue.hybridOverlayCoordinates());
        summary.put("expandedDependencyRuntimeEntries", summaryValue.expandedDependencyRuntimeEntries());
        summary.put("optimizedJarRuntimeEntries", summaryValue.optimizedJarRuntimeEntries());
        summary.put("originalFallbackJarEntries", summaryValue.originalFallbackJarEntries());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (com.yourorg.jmoa.plugin.modec.HybridOverlayClasspathEntry entry : summaryValue.overlayEntries()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("coordinate", entry.coordinate().displayName());
            item.put("expandedRoot", entry.expandedRoot() == null ? "" : entry.expandedRoot().getAbsolutePath());
            item.put("originalJar", entry.originalJar() == null ? "" : entry.originalJar().getAbsolutePath());
            entries.add(item);
        }
        summary.put("overlayEntries", entries);
        return summary;
    }

    private static Map<String, Object> buildReplacementCostSummary(
        GeneratedRuntimeArtifact generatedRuntimeArtifact,
        List<Tier2AdapterArtifact> tier2Artifacts,
        WeaveExecutionResult weaveExecutionResult,
        OptimizedDependencyJarPackagingResult packagingResult
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();

        long runtimeBytes = generatedRuntimeArtifact == null || generatedRuntimeArtifact.classBytes() == null
            ? 0L
            : generatedRuntimeArtifact.classBytes().length;
        long adapterBytes = tier2Artifacts == null
            ? 0L
            : tier2Artifacts.stream().mapToLong(artifact -> artifact.classBytes().length).sum();
        int adapterCount = tier2Artifacts == null ? 0 : tier2Artifacts.size();
        int maxAdapterBytes = tier2Artifacts == null
            ? 0
            : tier2Artifacts.stream().mapToInt(artifact -> artifact.classBytes().length).max().orElse(0);
        double averageAdapterBytes = adapterCount == 0 ? 0.0d : ((double) adapterBytes) / adapterCount;

        summary.put("generatedRuntimeClassBytes", runtimeBytes);
        summary.put("generatedRuntimeClassName", generatedRuntimeArtifact == null ? "" : generatedRuntimeArtifact.internalName());
        summary.put("generatedPackageAdapterCount", adapterCount);
        summary.put("generatedPackageAdapterClassBytes", adapterBytes);
        summary.put("averageGeneratedPackageAdapterClassBytes", averageAdapterBytes);
        summary.put("maxGeneratedPackageAdapterClassBytes", maxAdapterBytes);

        List<com.yourorg.jmoa.plugin.weave.RewrittenClassDelta> deltas =
            weaveExecutionResult == null ? List.of() : weaveExecutionResult.rewrittenClassDeltas();
        long totalOriginalBytes = deltas.stream().mapToLong(com.yourorg.jmoa.plugin.weave.RewrittenClassDelta::originalClassBytes).sum();
        long totalRewrittenBytes = deltas.stream().mapToLong(com.yourorg.jmoa.plugin.weave.RewrittenClassDelta::rewrittenClassBytes).sum();
        long totalByteDelta = deltas.stream().mapToLong(com.yourorg.jmoa.plugin.weave.RewrittenClassDelta::byteDelta).sum();
        long totalConstantPoolBefore = deltas.stream().mapToLong(com.yourorg.jmoa.plugin.weave.RewrittenClassDelta::constantPoolCountBefore).sum();
        long totalConstantPoolAfter = deltas.stream().mapToLong(com.yourorg.jmoa.plugin.weave.RewrittenClassDelta::constantPoolCountAfter).sum();
        long totalConstantPoolDelta = deltas.stream().mapToLong(com.yourorg.jmoa.plugin.weave.RewrittenClassDelta::constantPoolDelta).sum();
        long changedClassHashes = deltas.stream().filter(com.yourorg.jmoa.plugin.weave.RewrittenClassDelta::classBytesChanged).count();

        Map<String, Object> rewrittenClassGrowthSummary = new LinkedHashMap<>();
        rewrittenClassGrowthSummary.put("rewrittenClassesMeasured", deltas.size());
        rewrittenClassGrowthSummary.put("rewrittenClassesWithChangedHashes", changedClassHashes);
        rewrittenClassGrowthSummary.put("totalOriginalClassBytes", totalOriginalBytes);
        rewrittenClassGrowthSummary.put("totalRewrittenClassBytes", totalRewrittenBytes);
        rewrittenClassGrowthSummary.put("totalByteDelta", totalByteDelta);
        rewrittenClassGrowthSummary.put("averageByteDelta", deltas.isEmpty() ? 0.0d : ((double) totalByteDelta) / deltas.size());
        rewrittenClassGrowthSummary.put("topGrowthClasses", topGrowthClasses(deltas));
        summary.put("rewrittenClassGrowthSummary", rewrittenClassGrowthSummary);

        Map<String, Object> constantPoolGrowthSummary = new LinkedHashMap<>();
        constantPoolGrowthSummary.put("rewrittenClassesMeasured", deltas.size());
        constantPoolGrowthSummary.put("totalConstantPoolEntriesBefore", totalConstantPoolBefore);
        constantPoolGrowthSummary.put("totalConstantPoolEntriesAfter", totalConstantPoolAfter);
        constantPoolGrowthSummary.put("totalConstantPoolEntryDelta", totalConstantPoolDelta);
        constantPoolGrowthSummary.put("averageConstantPoolEntryDelta", deltas.isEmpty() ? 0.0d : ((double) totalConstantPoolDelta) / deltas.size());
        constantPoolGrowthSummary.put("topGrowthClasses", topGrowthClasses(deltas));
        summary.put("constantPoolGrowthSummary", constantPoolGrowthSummary);

        long optimizedJarDeltaBytes = packagingResult == null
            ? 0L
            : packagingResult.artifacts().stream().mapToLong(artifact -> artifact.optimizedJarBytes() - artifact.originalJarBytes()).sum();
        summary.put("optimizedDependencyJarCount", packagingResult == null ? 0 : packagingResult.artifacts().size());
        summary.put("optimizedDependencyJarByteDelta", optimizedJarDeltaBytes);
        return summary;
    }

    private static List<Map<String, Object>> topGrowthClasses(
        List<com.yourorg.jmoa.plugin.weave.RewrittenClassDelta> deltas
    ) {
        List<Map<String, Object>> entries = new ArrayList<>();
        deltas.stream()
            .sorted(Comparator.comparingInt(com.yourorg.jmoa.plugin.weave.RewrittenClassDelta::byteDelta).reversed())
            .limit(20)
            .forEach(delta -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("className", delta.classInternalName().replace('/', '.'));
                entry.put("classFile", delta.classFilePath());
                entry.put("originalClassBytes", delta.originalClassBytes());
                entry.put("rewrittenClassBytes", delta.rewrittenClassBytes());
                entry.put("byteDelta", delta.byteDelta());
                entry.put("constantPoolCountBefore", delta.constantPoolCountBefore());
                entry.put("constantPoolCountAfter", delta.constantPoolCountAfter());
                entry.put("constantPoolDelta", delta.constantPoolDelta());
                entry.put("originalClassHash", delta.originalClassHash());
                entry.put("rewrittenClassHash", delta.rewrittenClassHash());
                entry.put("classBytesChanged", delta.classBytesChanged());
                entries.add(entry);
            });
        return entries;
    }

    private static String toDuplicateGroupKey(com.yourorg.jmoa.plugin.dedup.DeduplicationKey key) {
        return key.samInterfaceInternalName()
            + "|"
            + key.samMethodDescriptor()
            + "|"
            + key.implTag()
            + "|"
            + key.implOwner()
            + "|"
            + key.implName()
            + "|"
            + key.implDesc()
            + "|"
            + key.instantiatedMethodType();
    }
}
