package com.yourorg.jmoa.plugin.measure;

import java.util.List;

public record MeasurementResult(
    MeasurementScenario scenario,
    String mainClass,
    int exitCode,
    String launchClassification,
    int totalLoadedClasses,
    int lambdaClasses,
    int applicationLambdaClasses,
    int frameworkLambdaClasses,
    int jmoaToolClasses,
    int jmoaRuntimeLibClasses,
    int jmoaGeneratedOptimizationClasses,
    int jmoaGeneratedPackageAdapterClasses,
    int jdkInternalClassfileClasses,
    int javaLangClassfileClasses,
    int springCoreClassReadingClasses,
    int totalUnloadedClasses,
    int unloadedLambdaClasses,
    int unloadedApplicationLambdaClasses,
    int unloadedFrameworkLambdaClasses,
    int unloadedJmoaRuntimeLibClasses,
    int unloadedJmoaGeneratedOptimizationClasses,
    int unloadedJmoaGeneratedPackageAdapterClasses,
    long metaspaceReservedKb,
    long metaspaceCommittedKb,
    long metaspaceUsedKb,
    long classSpaceReservedKb,
    long classSpaceCommittedKb,
    long classSpaceUsedKb,
    double averageStartupMs,
    int runs,
    List<Double> startupRunsMs
) {

    public MeasurementResult {
        startupRunsMs = startupRunsMs == null ? List.of() : List.copyOf(startupRunsMs);
    }

    public int jmoaGeneratedClasses() {
        return jmoaGeneratedOptimizationClasses + jmoaGeneratedPackageAdapterClasses;
    }

    public int unloadedJmoaGeneratedClasses() {
        return unloadedJmoaGeneratedOptimizationClasses + unloadedJmoaGeneratedPackageAdapterClasses;
    }
}
