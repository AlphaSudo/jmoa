package com.yourorg.jmoa.plugin.measure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MeasurementReportWriter {

    private MeasurementReportWriter() {
    }

    public static void write(
        File reportFile,
        MeasurementScenario baselineScenario,
        List<MeasurementPlan> plans,
        List<MeasurementResult> results,
        List<MeasurementComparison> comparisons,
        Map<MeasurementScenario, ClassLoadDiffAnalyzer.ClassLoadDiff> classDiffs
    ) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("timestamp", Instant.now().toString());
        report.put("metadataVersion", "v3.3-phase17");
        report.put("baselineScenario", baselineScenario.name());
        report.put("plans", plans.stream().map(MeasurementReportWriter::toPlanMap).toList());
        report.put("results", results.stream().map(MeasurementReportWriter::toResultMap).toList());
        report.put("comparisons", comparisons.stream().map(MeasurementReportWriter::toComparisonMap).toList());
        report.put("classDiffs", classDiffs.entrySet().stream().map(entry -> toClassDiffMap(entry.getKey(), entry.getValue())).toList());
        if (comparisons.size() == 1) {
            report.put("comparison", toComparisonMap(comparisons.get(0)));
            report.put("candidateScenario", comparisons.get(0).candidateScenario().name());
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(reportFile, report);
    }

    private static Map<String, Object> toPlanMap(MeasurementPlan plan) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("scenario", plan.scenario().name());
        map.put("mainClass", plan.mainClass());
        map.put("prettyCommand", plan.prettyCommand());
        map.put("classLoadLogFile", plan.classLoadLogFile().getAbsolutePath());
        map.put("nmtLogFile", plan.nmtLogFile().getAbsolutePath());
        map.put("scenarioResultFile", plan.scenarioResultFile().getAbsolutePath());
        return map;
    }

    private static Map<String, Object> toResultMap(MeasurementResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("scenario", result.scenario().name());
        map.put("mainClass", result.mainClass());
        map.put("exitCode", result.exitCode());
        map.put("launchClassification", result.launchClassification());
        map.put("totalLoadedClasses", result.totalLoadedClasses());
        map.put("lambdaClasses", result.lambdaClasses());
        map.put("applicationLambdaClasses", result.applicationLambdaClasses());
        map.put("frameworkLambdaClasses", result.frameworkLambdaClasses());
        map.put("jmoaToolClasses", result.jmoaToolClasses());
        map.put("jmoaRuntimeLibClasses", result.jmoaRuntimeLibClasses());
        map.put("jmoaGeneratedOptimizationClasses", result.jmoaGeneratedOptimizationClasses());
        map.put("jmoaGeneratedPackageAdapterClasses", result.jmoaGeneratedPackageAdapterClasses());
        map.put("jmoaGeneratedClasses", result.jmoaGeneratedClasses());
        map.put("jdkInternalClassfileClasses", result.jdkInternalClassfileClasses());
        map.put("javaLangClassfileClasses", result.javaLangClassfileClasses());
        map.put("springCoreClassReadingClasses", result.springCoreClassReadingClasses());
        map.put("totalUnloadedClasses", result.totalUnloadedClasses());
        map.put("unloadedLambdaClasses", result.unloadedLambdaClasses());
        map.put("unloadedApplicationLambdaClasses", result.unloadedApplicationLambdaClasses());
        map.put("unloadedFrameworkLambdaClasses", result.unloadedFrameworkLambdaClasses());
        map.put("unloadedJmoaRuntimeLibClasses", result.unloadedJmoaRuntimeLibClasses());
        map.put("unloadedJmoaGeneratedOptimizationClasses", result.unloadedJmoaGeneratedOptimizationClasses());
        map.put("unloadedJmoaGeneratedPackageAdapterClasses", result.unloadedJmoaGeneratedPackageAdapterClasses());
        map.put("unloadedJmoaGeneratedClasses", result.unloadedJmoaGeneratedClasses());
        map.put("metaspaceReservedKb", result.metaspaceReservedKb());
        map.put("metaspaceCommittedKb", result.metaspaceCommittedKb());
        map.put("metaspaceUsedKb", result.metaspaceUsedKb());
        map.put("classSpaceReservedKb", result.classSpaceReservedKb());
        map.put("classSpaceCommittedKb", result.classSpaceCommittedKb());
        map.put("classSpaceUsedKb", result.classSpaceUsedKb());
        map.put("averageStartupMs", result.averageStartupMs());
        map.put("runs", result.runs());
        map.put("startupRunsMs", result.startupRunsMs());
        return map;
    }

    private static Map<String, Object> toComparisonMap(MeasurementComparison comparison) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("baselineScenario", comparison.baselineScenario().name());
        map.put("candidateScenario", comparison.candidateScenario().name());
        map.put("lambdaReductionAbsolute", comparison.lambdaReductionAbsolute());
        map.put("lambdaReductionPercent", comparison.lambdaReductionPercent());
        map.put("metaspaceCommittedReductionKb", comparison.metaspaceCommittedReductionKb());
        map.put("metaspaceCommittedReductionPercent", comparison.metaspaceCommittedReductionPercent());
        map.put("startupChangeMs", comparison.startupChangeMs());
        map.put("startupChangePercent", comparison.startupChangePercent());
        map.put("passesThresholds", comparison.passesThresholds());
        map.put("verdict", comparison.verdict());
        return map;
    }

    private static Map<String, Object> toClassDiffMap(
        MeasurementScenario candidateScenario,
        ClassLoadDiffAnalyzer.ClassLoadDiff diff
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("candidateScenario", candidateScenario.name());
        map.put("baselineOnlyClasses", diff.baselineOnlyClasses());
        map.put("candidateOnlyClasses", diff.candidateOnlyClasses());
        map.put("sharedClassCount", diff.sharedClasses().size());
        map.put("removedLambdaClasses", diff.removedLambdaClasses());
        map.put("addedJmoaToolClasses", diff.addedJmoaToolClasses());
        map.put("addedJmoaRuntimeLibClasses", diff.addedJmoaRuntimeLibClasses());
        map.put("addedJmoaGeneratedOptimizationClasses", diff.addedJmoaGeneratedOptimizationClasses());
        map.put("addedJmoaGeneratedPackageAdapterClasses", diff.addedJmoaGeneratedPackageAdapterClasses());
        map.put("addedNormalFrameworkClasses", diff.addedNormalFrameworkClasses());
        map.put("removedNormalFrameworkClasses", diff.removedNormalFrameworkClasses());
        map.put("addedNormalFrameworkPackages", diff.addedNormalFrameworkPackages());
        map.put("removedNormalFrameworkPackages", diff.removedNormalFrameworkPackages());
        map.put("topAddedNormalFrameworkPackages", diff.topAddedPackages(20));
        map.put("topRemovedNormalFrameworkPackages", diff.topRemovedPackages(20));
        return map;
    }
}
