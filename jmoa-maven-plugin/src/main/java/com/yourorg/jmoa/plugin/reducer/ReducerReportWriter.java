package com.yourorg.jmoa.plugin.reducer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReducerReportWriter {

    public void write(File outputDir, ReducerReport report) throws IOException {
        if (outputDir != null) {
            outputDir.mkdirs();
        }
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(outputDir, "reducer-build-report.json"), report);
        mapper.writeValue(new File(outputDir, "debug-metadata-savings-estimate.json"), savings(report));
        mapper.writeValue(new File(outputDir, "bytecode-reducer-safety-taxonomy.json"), report.safetyTaxonomy());
        writeReducerMarkdown(new File(outputDir, "reducer-build-report.md"), report);
        writeSavingsMarkdown(new File(outputDir, "debug-metadata-savings-estimate.md"), report);
        writeTaxonomyMarkdown(new File(outputDir, "bytecode-reducer-safety-taxonomy.md"), report.safetyTaxonomy());
    }

    private static Map<String, Object> savings(ReducerReport report) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2-e2-debug-metadata-savings-estimate");
        root.put("generatedAt", report.generatedAt());
        root.put("mutationEnabled", report.mutationEnabled());
        root.put("jarCount", report.jarCount());
        root.put("classCount", report.classCount());
        root.put("totalEstimatedRemovableBytes", report.totalEstimatedRemovableBytes());
        root.put("totalRemovedBytes", report.totalRemovedBytes());
        root.put("artifacts", report.artifacts());
        return root;
    }

    private static void writeReducerMarkdown(File file, ReducerReport report) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# V2-E Reducer Build Report\n\n");
        md.append("- Metadata version: `").append(report.metadataVersion()).append("`\n");
        md.append("- Mutation enabled: `").append(report.mutationEnabled()).append("`\n");
        md.append("- Report only: `").append(report.reportOnly()).append("`\n");
        md.append("- Profile: `").append(report.profile()).append("`\n");
        md.append("- Jars: `").append(report.jarCount()).append("`\n");
        md.append("- Classes: `").append(report.classCount()).append("`\n");
        md.append("- Estimated removable bytes: `").append(report.totalEstimatedRemovableBytes()).append("`\n");
        md.append("- Removed bytes: `").append(report.totalRemovedBytes()).append("`\n\n");
        md.append("## Artifacts\n\n");
        md.append("| Artifact | Classes | Reduced classes | Original bytes | Reduced bytes | Estimated removable | Removed | Status |\n");
        md.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (JarReductionRecord jar : report.artifacts()) {
            md.append("| `").append(jar.artifact()).append("` | ")
                .append(jar.classCount()).append(" | ")
                .append(jar.reducedClassCount()).append(" | ")
                .append(jar.originalBytes()).append(" | ")
                .append(jar.reducedBytes()).append(" | ")
                .append(jar.estimatedRemovableBytes()).append(" | ")
                .append(jar.removedBytes()).append(" | `")
                .append(jar.status()).append("` |\n");
        }
        md.append("\n## Boundary\n\n");
        for (String boundary : report.boundaries()) {
            md.append("- ").append(boundary).append('\n');
        }
        Files.writeString(file.toPath(), md.toString(), StandardCharsets.UTF_8);
    }

    private static void writeSavingsMarkdown(File file, ReducerReport report) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Debug Metadata Savings Estimate\n\n");
        md.append("| Artifact | Estimated removable bytes | Top removable class count |\n");
        md.append("| --- | ---: | ---: |\n");
        for (JarReductionRecord jar : report.artifacts().stream()
            .sorted(Comparator.comparingLong(JarReductionRecord::estimatedRemovableBytes).reversed())
            .toList()) {
            md.append("| `").append(jar.artifact()).append("` | ")
                .append(jar.estimatedRemovableBytes()).append(" | ")
                .append(jar.classes().size()).append(" |\n");
        }
        md.append("\nOnly `LocalVariableTable` and `LocalVariableTypeTable` are counted as removable in V2-E.\n");
        Files.writeString(file.toPath(), md.toString(), StandardCharsets.UTF_8);
    }

    private static void writeTaxonomyMarkdown(File file, ReducerSafetyTaxonomy taxonomy) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Bytecode Reducer Safety Taxonomy\n\n");
        md.append("| Attribute | Category | Reason |\n");
        md.append("| --- | --- | --- |\n");
        for (ReducerSafetyEntry entry : taxonomy.entries()) {
            md.append("| `").append(entry.attribute()).append("` | `")
                .append(entry.category()).append("` | ")
                .append(entry.reason()).append(" |\n");
        }
        Files.writeString(file.toPath(), md.toString(), StandardCharsets.UTF_8);
    }
}
