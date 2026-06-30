package com.yourorg.jmoa.plugin.measure;

import java.io.File;
import java.util.List;

public record MeasurementConfig(
    MeasurementScenario scenario,
    File outputDirectory,
    String mainClass,
    List<String> measurementArgs,
    String targetMainClass,
    int measurementRuns,
    boolean executeMeasurements,
    boolean failOnMeasurementRegression,
    Integer minLambdaClassReduction,
    Integer minMetaspaceReductionKb
) {

    public MeasurementConfig {
        measurementArgs = measurementArgs == null ? List.of() : List.copyOf(measurementArgs);
        targetMainClass = targetMainClass == null ? mainClass : targetMainClass;
        measurementRuns = Math.max(1, measurementRuns);
    }
}
