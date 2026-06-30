package com.yourorg.jmoa.plugin.measure;

public record MeasurementComparison(
    MeasurementScenario baselineScenario,
    MeasurementScenario candidateScenario,
    int lambdaReductionAbsolute,
    double lambdaReductionPercent,
    long metaspaceCommittedReductionKb,
    double metaspaceCommittedReductionPercent,
    double startupChangeMs,
    double startupChangePercent,
    boolean passesThresholds,
    String verdict
) {
}
