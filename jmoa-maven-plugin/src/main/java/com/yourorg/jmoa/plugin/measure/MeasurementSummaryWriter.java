package com.yourorg.jmoa.plugin.measure;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MeasurementSummaryWriter {

    public void write(
        File summaryFile,
        MeasurementScenario baselineScenario,
        List<MeasurementResult> results,
        List<MeasurementComparison> comparisons
    ) throws IOException {
        Map<MeasurementScenario, MeasurementResult> byScenario = new LinkedHashMap<>();
        for (MeasurementResult result : results) {
            byScenario.put(result.scenario(), result);
        }

        StringBuilder markdown = new StringBuilder();
        markdown.append("# JMOA Measurement Summary").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("Baseline scenario: `").append(baselineScenario.name()).append("`")
            .append(System.lineSeparator()).append(System.lineSeparator());

        markdown.append("## Scenario Results").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("| Scenario | Exit | Lambda Classes | Lambda Unloaded | Framework Lambda Classes | Framework Lambda Unloaded | JMOA Tool | JMOA Runtime | JMOA Generated | Package Adapters | JDK Classfile | Java Classfile | Spring Classreading | JMOA Generated Unloaded | Metaspace Used (KB) | Metaspace Committed (KB) | Class Space Used (KB) | Class Space Committed (KB) | Avg Startup (ms) | Verdict |")
            .append(System.lineSeparator());
        markdown.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |").append(System.lineSeparator());
        for (MeasurementResult result : results) {
            String verdict = result.scenario() == baselineScenario
                ? "BASELINE"
                : comparisons.stream()
                    .filter(comparison -> comparison.candidateScenario() == result.scenario())
                    .map(MeasurementComparison::verdict)
                    .findFirst()
                    .orElse("N/A");
            markdown.append("| `").append(result.scenario().name()).append("` | `")
                .append(result.launchClassification()).append("` (").append(result.exitCode()).append(") | ")
                .append(result.lambdaClasses()).append(" | ")
                .append(result.unloadedLambdaClasses()).append(" | ")
                .append(result.frameworkLambdaClasses()).append(" | ")
                .append(result.unloadedFrameworkLambdaClasses()).append(" | ")
                .append(result.jmoaToolClasses()).append(" | ")
                .append(result.jmoaRuntimeLibClasses()).append(" | ")
                .append(result.jmoaGeneratedOptimizationClasses()).append(" | ")
                .append(result.jmoaGeneratedPackageAdapterClasses()).append(" | ")
                .append(result.jdkInternalClassfileClasses()).append(" | ")
                .append(result.javaLangClassfileClasses()).append(" | ")
                .append(result.springCoreClassReadingClasses()).append(" | ")
                .append(result.unloadedJmoaGeneratedClasses()).append(" | ")
                .append(result.metaspaceUsedKb()).append(" | ")
                .append(result.metaspaceCommittedKb()).append(" | ")
                .append(result.classSpaceUsedKb()).append(" | ")
                .append(result.classSpaceCommittedKb()).append(" | ")
                .append(String.format("%.3f", result.averageStartupMs())).append(" | ")
                .append(verdict).append(" |")
                .append(System.lineSeparator());
        }

        if (!comparisons.isEmpty()) {
            markdown.append(System.lineSeparator());
            markdown.append("## Baseline Comparisons").append(System.lineSeparator()).append(System.lineSeparator());
            markdown.append("| Candidate | Lambda Reduction | Metaspace Reduction (KB) | Startup Change (ms) | Verdict |")
                .append(System.lineSeparator());
            markdown.append("| --- | ---: | ---: | ---: | --- |").append(System.lineSeparator());
            for (MeasurementComparison comparison : comparisons) {
                markdown.append("| `").append(comparison.candidateScenario().name()).append("` | ")
                    .append(comparison.lambdaReductionAbsolute()).append(" (")
                    .append(String.format("%.2f", comparison.lambdaReductionPercent())).append("%) | ")
                    .append(comparison.metaspaceCommittedReductionKb()).append(" (")
                    .append(String.format("%.2f", comparison.metaspaceCommittedReductionPercent())).append("%) | ")
                    .append(String.format("%.3f", comparison.startupChangeMs())).append(" (")
                    .append(String.format("%.2f", comparison.startupChangePercent())).append("%) | ")
                    .append(comparison.verdict()).append(" |")
                    .append(System.lineSeparator());
            }
        }

        Files.writeString(summaryFile.toPath(), markdown.toString(), StandardCharsets.UTF_8);
    }
}
