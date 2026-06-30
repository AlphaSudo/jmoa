package com.yourorg.jmoa.plugin.measure;

import java.io.File;
import java.util.List;

public record MeasurementPlan(
    MeasurementScenario scenario,
    String mainClass,
    List<String> javaCommand,
    String prettyCommand,
    File classLoadLogFile,
    File nmtLogFile,
    File scenarioResultFile
) {

    public MeasurementPlan {
        javaCommand = javaCommand == null ? List.of() : List.copyOf(javaCommand);
    }
}
