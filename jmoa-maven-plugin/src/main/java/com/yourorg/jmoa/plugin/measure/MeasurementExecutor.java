package com.yourorg.jmoa.plugin.measure;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class MeasurementExecutor {

    private static final String LAUNCH_CLASSIFICATION_PREFIX = "JMOA_MODE_C_LAUNCH_CLASSIFICATION=";
    private static final String ENVIRONMENT_WALL = "ENVIRONMENT_WALL";

    private final ClassLoadLogParser classLoadLogParser;
    private final NmtSummaryParser nmtSummaryParser;

    public MeasurementExecutor() {
        this(new ClassLoadLogParser(), new NmtSummaryParser());
    }

    public MeasurementExecutor(
        ClassLoadLogParser classLoadLogParser,
        NmtSummaryParser nmtSummaryParser
    ) {
        this.classLoadLogParser = classLoadLogParser;
        this.nmtSummaryParser = nmtSummaryParser;
    }

    public MeasurementResult execute(MeasurementPlan plan, int runs) throws IOException, InterruptedException {
        File outputDirectory = plan.scenarioResultFile().getParentFile();
        if (outputDirectory != null) {
            Files.createDirectories(outputDirectory.toPath());
        }

        List<Double> startupRunsMs = new ArrayList<>();
        int finalExitCode = 0;
        String finalLaunchClassification = "NONE";
        for (int run = 1; run <= Math.max(1, runs); run++) {
            long start = System.nanoTime();
            ProcessBuilder processBuilder = new ProcessBuilder(plan.javaCommand());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(plan.nmtLogFile());
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            long elapsedNanos = System.nanoTime() - start;
            startupRunsMs.add(elapsedNanos / 1_000_000.0d);
            finalExitCode = exitCode;

            String nmtOutput = Files.exists(plan.nmtLogFile().toPath())
                ? Files.readString(plan.nmtLogFile().toPath(), StandardCharsets.UTF_8)
                : "";
            finalLaunchClassification = extractLaunchClassification(nmtOutput);
            if (exitCode != 0 && !isAcceptedEnvironmentWall(exitCode, finalLaunchClassification)) {
                throw new IOException("Measurement process failed for " + plan.scenario()
                    + " with exit code " + exitCode + ". Command: " + plan.prettyCommand()
                    + System.lineSeparator() + nmtOutput);
            }
        }

        ClassLoadLogParser.ClassLoadMetrics classLoadMetrics = classLoadLogParser.parse(plan.classLoadLogFile());
        NmtSummaryParser.NmtMetrics nmtMetrics = nmtSummaryParser.parse(plan.nmtLogFile());
        double averageStartupMs = startupRunsMs.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0d);

        return new MeasurementResult(
            plan.scenario(),
            plan.mainClass(),
            finalExitCode,
            finalLaunchClassification,
            classLoadMetrics.totalLoadedClasses(),
            classLoadMetrics.lambdaClasses(),
            classLoadMetrics.applicationLambdaClasses(),
            classLoadMetrics.frameworkLambdaClasses(),
            classLoadMetrics.jmoaToolClasses(),
            classLoadMetrics.jmoaRuntimeLibClasses(),
            classLoadMetrics.jmoaGeneratedOptimizationClasses(),
            classLoadMetrics.jmoaGeneratedPackageAdapterClasses(),
            classLoadMetrics.jdkInternalClassfileClasses(),
            classLoadMetrics.javaLangClassfileClasses(),
            classLoadMetrics.springCoreClassReadingClasses(),
            classLoadMetrics.totalUnloadedClasses(),
            classLoadMetrics.unloadedLambdaClasses(),
            classLoadMetrics.unloadedApplicationLambdaClasses(),
            classLoadMetrics.unloadedFrameworkLambdaClasses(),
            classLoadMetrics.unloadedJmoaRuntimeLibClasses(),
            classLoadMetrics.unloadedJmoaGeneratedOptimizationClasses(),
            classLoadMetrics.unloadedJmoaGeneratedPackageAdapterClasses(),
            nmtMetrics.metaspaceReservedKb(),
            nmtMetrics.metaspaceCommittedKb(),
            nmtMetrics.metaspaceUsedKb(),
            nmtMetrics.classSpaceReservedKb(),
            nmtMetrics.classSpaceCommittedKb(),
            nmtMetrics.classSpaceUsedKb(),
            averageStartupMs,
            startupRunsMs.size(),
            startupRunsMs
        );
    }

    private boolean isAcceptedEnvironmentWall(int exitCode, String launchClassification) {
        return exitCode == 20 && ENVIRONMENT_WALL.equals(launchClassification);
    }

    private String extractLaunchClassification(String output) {
        if (output == null || output.isBlank()) {
            return "NONE";
        }
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(LAUNCH_CLASSIFICATION_PREFIX)) {
                return trimmed.substring(LAUNCH_CLASSIFICATION_PREFIX.length()).trim();
            }
        }
        return "NONE";
    }
}
