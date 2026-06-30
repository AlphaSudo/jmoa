package com.yourorg.jmoa.plugin.measure;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MeasurementCommandWriter {

    public MeasurementPlan buildPlan(
        MeasurementConfig config,
        File javaExecutable,
        String classpath
    ) {
        File outputDir = config.outputDirectory();
        File classLoadLog = new File(outputDir, config.scenario().name().toLowerCase() + "-classload.log");
        File nmtLog = new File(outputDir, config.scenario().name().toLowerCase() + "-nmt.log");
        File scenarioResult = new File(outputDir, "jmoa-measurement-" + config.scenario().name().toLowerCase() + ".json");

        List<String> command = new ArrayList<>();
        command.add(javaExecutable.getAbsolutePath());
        command.add("-Xlog:class+load=info,class+unload=info:file=" + classLoadLog.getAbsolutePath());
        command.add("-XX:NativeMemoryTracking=summary");
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-XX:+PrintNMTStatistics");
        command.add("-cp");
        command.add(classpath);
        command.add(config.mainClass());
        command.addAll(config.measurementArgs());

        return new MeasurementPlan(
            config.scenario(),
            config.targetMainClass(),
            command,
            quoteCommand(command),
            classLoadLog,
            nmtLog,
            scenarioResult
        );
    }

    private String quoteCommand(List<String> command) {
        return command.stream()
            .map(part -> part.contains(" ") ? "\"" + part + "\"" : part)
            .reduce((left, right) -> left + " " + right)
            .orElse("");
    }
}
