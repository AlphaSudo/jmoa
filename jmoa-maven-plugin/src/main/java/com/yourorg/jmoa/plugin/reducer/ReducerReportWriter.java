package com.yourorg.jmoa.plugin.reducer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
        mapper.writeValue(new File(outputDir, "v2f-jar-safety-report.json"), jarSafety(report));
        mapper.writeValue(new File(outputDir, "jmoa-reducer-manifest.json"), reducerManifest(report));
        writeReducerMarkdown(new File(outputDir, "reducer-build-report.md"), report);
        writeSavingsMarkdown(new File(outputDir, "debug-metadata-savings-estimate.md"), report);
        writeTaxonomyMarkdown(new File(outputDir, "bytecode-reducer-safety-taxonomy.md"), report.safetyTaxonomy());
        writeJarSafetyMarkdown(new File(outputDir, "v2f-jar-safety-report.md"), report);
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

    private static Map<String, Object> jarSafety(ReducerReport report) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2f-jar-safety-report");
        root.put("generatedAt", report.generatedAt());
        root.put("signedJarsDetected", report.artifacts().stream().filter(JarReductionRecord::signedJar).count());
        root.put("multiReleaseJarsDetected", report.artifacts().stream().filter(JarReductionRecord::multiReleaseJar).count());
        root.put("sealedJarsDetected", report.artifacts().stream().filter(JarReductionRecord::sealedJar).count());
        root.put("signedJarsSkipped", skipReasonCount(report, "SKIPPED_SIGNED_JAR"));
        root.put("multiReleaseJarsSkipped", skipReasonCount(report, "SKIPPED_MULTI_RELEASE_JAR"));
        root.put("sealedJarsSkipped", skipReasonCount(report, "SKIPPED_SEALED_JAR"));
        root.put("bootstrapMethodsClassesSkipped", report.artifacts().stream()
            .mapToInt(JarReductionRecord::skippedBootstrapMethodsClassCount).sum());
        root.put("artifacts", report.artifacts().stream()
            .map(jar -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("artifact", jar.artifact());
                entry.put("signedJar", jar.signedJar());
                entry.put("multiReleaseJar", jar.multiReleaseJar());
                entry.put("sealedJar", jar.sealedJar());
                entry.put("skipReason", jar.skipReason());
                entry.put("status", jar.status());
                entry.put("classCount", jar.classCount());
                entry.put("reducedClassCount", jar.reducedClassCount());
                entry.put("skippedBootstrapMethodsClassCount", jar.skippedBootstrapMethodsClassCount());
                return entry;
            })
            .toList());
        return root;
    }

    private static Map<String, Object> reducerManifest(ReducerReport report) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2f-jmoa-reducer-manifest");
        root.put("generatedAt", report.generatedAt());
        root.put("profile", report.profile());
        root.put("mutationEnabled", report.mutationEnabled());
        root.put("timestampPolicy", "preserve");
        root.put("strippedAttributes", report.mutationEnabled()
            ? List.of("LocalVariableTable", "LocalVariableTypeTable")
            : List.of());
        root.put("preservedAttributes", List.of(
            "LineNumberTable",
            "SourceFile",
            "StackMapTable",
            "RuntimeVisibleAnnotations",
            "RuntimeInvisibleAnnotations",
            "Signature",
            "BootstrapMethods",
            "module-info.class"
        ));
        root.put("artifacts", report.artifacts().stream()
            .map(jar -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("inputJar", jar.sourceJar());
                entry.put("outputJar", jar.reducedJar());
                entry.put("inputSha256", jar.inputSha256());
                entry.put("outputSha256", jar.outputSha256());
                entry.put("originalBytes", jar.originalBytes());
                entry.put("reducedBytes", jar.reducedBytes());
                entry.put("removedBytes", jar.removedBytes());
                entry.put("classesScanned", jar.classCount());
                entry.put("classesReduced", jar.reducedClassCount());
                entry.put("classesSkippedBootstrapMethods", jar.skippedBootstrapMethodsClassCount());
                entry.put("signedJar", jar.signedJar());
                entry.put("multiReleaseJar", jar.multiReleaseJar());
                entry.put("sealedJar", jar.sealedJar());
                entry.put("skipReason", jar.skipReason());
                entry.put("status", jar.status());
                entry.put("timestampPolicy", jar.timestampPolicy());
                return entry;
            })
            .toList());
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
        md.append("- Signed jars detected: `").append(report.artifacts().stream().filter(JarReductionRecord::signedJar).count()).append("`\n");
        md.append("- Multi-release jars detected: `").append(report.artifacts().stream().filter(JarReductionRecord::multiReleaseJar).count()).append("`\n");
        md.append("- Sealed jars detected: `").append(report.artifacts().stream().filter(JarReductionRecord::sealedJar).count()).append("`\n");
        md.append("- Signed jars skipped by policy: `").append(skipReasonCount(report, "SKIPPED_SIGNED_JAR")).append("`\n");
        md.append("- Multi-release jars skipped by policy: `").append(skipReasonCount(report, "SKIPPED_MULTI_RELEASE_JAR")).append("`\n");
        md.append("- Sealed jars skipped by policy: `").append(skipReasonCount(report, "SKIPPED_SEALED_JAR")).append("`\n");
        md.append("- BootstrapMethods classes skipped: `").append(report.artifacts().stream()
            .mapToInt(JarReductionRecord::skippedBootstrapMethodsClassCount).sum()).append("`\n\n");
        md.append("## Artifacts\n\n");
        md.append("| Artifact | Classes | Reduced classes | Original bytes | Reduced bytes | Estimated removable | Removed | Skip reason | Status |\n");
        md.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |\n");
        for (JarReductionRecord jar : report.artifacts()) {
            md.append("| `").append(jar.artifact()).append("` | ")
                .append(jar.classCount()).append(" | ")
                .append(jar.reducedClassCount()).append(" | ")
                .append(jar.originalBytes()).append(" | ")
                .append(jar.reducedBytes()).append(" | ")
                .append(jar.estimatedRemovableBytes()).append(" | ")
                .append(jar.removedBytes()).append(" | `")
                .append(nullToEmpty(jar.skipReason())).append("` | `")
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

    private static void writeJarSafetyMarkdown(File file, ReducerReport report) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# V2-F Jar Safety Report\n\n");
        md.append("- Signed jars detected: `").append(report.artifacts().stream().filter(JarReductionRecord::signedJar).count()).append("`\n");
        md.append("- Multi-release jars detected: `").append(report.artifacts().stream().filter(JarReductionRecord::multiReleaseJar).count()).append("`\n");
        md.append("- Sealed jars detected: `").append(report.artifacts().stream().filter(JarReductionRecord::sealedJar).count()).append("`\n");
        md.append("- Signed jars skipped by policy: `").append(skipReasonCount(report, "SKIPPED_SIGNED_JAR")).append("`\n");
        md.append("- Multi-release jars skipped by policy: `").append(skipReasonCount(report, "SKIPPED_MULTI_RELEASE_JAR")).append("`\n");
        md.append("- Sealed jars skipped by policy: `").append(skipReasonCount(report, "SKIPPED_SEALED_JAR")).append("`\n\n");
        md.append("| Artifact | Signed | Multi-release | Sealed | BootstrapMethods classes skipped | Skip reason | Status |\n");
        md.append("| --- | ---: | ---: | ---: | ---: | --- | --- |\n");
        for (JarReductionRecord jar : report.artifacts()) {
            md.append("| `").append(jar.artifact()).append("` | ")
                .append(jar.signedJar()).append(" | ")
                .append(jar.multiReleaseJar()).append(" | ")
                .append(jar.sealedJar()).append(" | ")
                .append(jar.skippedBootstrapMethodsClassCount()).append(" | `")
                .append(nullToEmpty(jar.skipReason())).append("` | `")
                .append(jar.status()).append("` |\n");
        }
        md.append("\nDefault V2-F policy: signed, multi-release, and sealed jars are copied unchanged and marked skipped.\n");
        Files.writeString(file.toPath(), md.toString(), StandardCharsets.UTF_8);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static long skipReasonCount(ReducerReport report, String skipReason) {
        return report.artifacts().stream()
            .filter(jar -> skipReason.equals(jar.skipReason()))
            .count();
    }
}
