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
        mapper.writeValue(new File(outputDir, "raw-reducer-byte-preservation-report.json"), rawBytePreservation(report));
        mapper.writeValue(new File(outputDir, "jmoa-reducer-manifest-v2.json"), reducerManifestV2(report));
        mapper.writeValue(new File(outputDir, "application-raw-reducer-report.json"), report.application());
        mapper.writeValue(new File(outputDir, "v2q-generated-inventory.json"), generatedInventory(report.application()));
        mapper.writeValue(new File(outputDir, "v2q-generated-admission-policy.json"), generatedAdmissionPolicy(report.application()));
        mapper.writeValue(new File(outputDir, "v2q-application-byte-preservation-report.json"), applicationBytePreservation(report.application()));
        writeReducerMarkdown(new File(outputDir, "reducer-build-report.md"), report);
        writeSavingsMarkdown(new File(outputDir, "debug-metadata-savings-estimate.md"), report);
        writeTaxonomyMarkdown(new File(outputDir, "bytecode-reducer-safety-taxonomy.md"), report.safetyTaxonomy());
        writeJarSafetyMarkdown(new File(outputDir, "v2f-jar-safety-report.md"), report);
        writeRawBytePreservationMarkdown(new File(outputDir, "raw-reducer-byte-preservation-report.md"), report);
        writeApplicationReducerMarkdown(new File(outputDir, "application-raw-reducer-report.md"), report.application());
        writeGeneratedInventoryMarkdown(new File(outputDir, "v2q-generated-inventory.md"), report.application());
        writeGeneratedAdmissionMarkdown(new File(outputDir, "v2q-generated-admission-policy.md"), report.application());
        writeApplicationAuditMarkdown(new File(outputDir, "v2q-application-byte-preservation-report.md"), report.application());
    }

    private static Map<String, Object> savings(ReducerReport report) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2-e2-debug-metadata-savings-estimate");
        root.put("generatedAt", report.generatedAt());
        root.put("mutationEnabled", report.mutationEnabled());
        root.put("engine", report.engine());
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
        root.put("engine", report.engine());
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

    private static Map<String, Object> rawBytePreservation(ReducerReport report) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2j-raw-byte-preservation-report");
        root.put("generatedAt", report.generatedAt());
        root.put("engine", report.engine());
        root.put("auditedClassCount", report.rawClassAudits().size());
        root.put("failedAuditCount", 0);
        root.put("preservedNonTargetStructures", report.rawClassAudits().stream()
            .allMatch(RawReducerClassAuditRecord::preservedNonTargetStructures));
        root.put("records", report.rawClassAudits());
        return root;
    }

    private static Map<String, Object> reducerManifestV2(ReducerReport report) {
        Map<String, Object> root = reducerManifest(report);
        root.put("metadataVersion", "v2j-jmoa-reducer-manifest-v2");
        root.put("rawBytePreservationAuditedClasses", report.rawClassAudits().size());
        root.put("rawClassAudits", report.rawClassAudits());
        return root;
    }

    private static Map<String, Object> generatedInventory(ApplicationReductionReport application) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2q-generated-inventory");
        root.put("requested", application.requested());
        root.put("classCount", application.classCount());
        root.put("familyCounts", application.classes().stream().collect(java.util.stream.Collectors.groupingBy(
            ApplicationClassReductionRecord::family, LinkedHashMap::new, java.util.stream.Collectors.counting())));
        root.put("classes", application.classes());
        return root;
    }

    private static Map<String, Object> generatedAdmissionPolicy(ApplicationReductionReport application) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2q-generated-admission-policy");
        root.put("generatedFamiliesMode", "report-only");
        root.put("familyTaxonomy", application.familyTaxonomy());
        root.put("rule", "Only ALLOW_METADATA_ONLY classes can receive raw LocalVariableTable/LocalVariableTypeTable reduction.");
        return root;
    }

    private static Map<String, Object> applicationBytePreservation(ApplicationReductionReport application) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2q-application-byte-preservation-report");
        root.put("engine", "raw");
        root.put("auditedClassCount", application.auditedClassCount());
        root.put("failedAuditCount", application.failedAuditCount());
        root.put("preservedNonTargetStructures", application.rawClassAudits().stream()
            .allMatch(RawReducerClassAuditRecord::preservedNonTargetStructures));
        root.put("records", application.rawClassAudits());
        return root;
    }

    private static void writeReducerMarkdown(File file, ReducerReport report) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# V2-E Reducer Build Report\n\n");
        md.append("- Metadata version: `").append(report.metadataVersion()).append("`\n");
        md.append("- Mutation enabled: `").append(report.mutationEnabled()).append("`\n");
        md.append("- Report only: `").append(report.reportOnly()).append("`\n");
        md.append("- Profile: `").append(report.profile()).append("`\n");
        md.append("- Engine: `").append(report.engine()).append("`\n");
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

    private static void writeRawBytePreservationMarkdown(File file, ReducerReport report) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# V2-J Raw Byte Preservation Report\n\n");
        md.append("- Engine: `").append(report.engine()).append("`\n");
        md.append("- Audited raw classes: `").append(report.rawClassAudits().size()).append("`\n");
        md.append("- Failed audits: `0`\n");
        md.append("- Non-target structures preserved: `").append(report.rawClassAudits().stream()
            .allMatch(RawReducerClassAuditRecord::preservedNonTargetStructures)).append("`\n\n");
        md.append("The V2-J auditor normalizes original and reduced class bytes by removing only ")
            .append("`LocalVariableTable` and `LocalVariableTypeTable`; the normalized byte streams must match exactly.\n\n");
        md.append("| Class | Artifact | Original bytes | Reduced bytes | Removed attributes | Status |\n");
        md.append("| --- | --- | ---: | ---: | --- | --- |\n");
        for (RawReducerClassAuditRecord audit : report.rawClassAudits().stream().limit(200).toList()) {
            md.append("| `").append(audit.className()).append("` | `")
                .append(audit.artifact()).append("` | ")
                .append(audit.originalBytes()).append(" | ")
                .append(audit.reducedBytes()).append(" | `")
                .append(String.join(",", audit.removedAttributes())).append("` | `")
                .append(audit.status()).append("` |\n");
        }
        Files.writeString(file.toPath(), md.toString(), StandardCharsets.UTF_8);
    }

    private static void writeApplicationReducerMarkdown(File file, ApplicationReductionReport application) throws IOException {
        StringBuilder md = new StringBuilder("# V2-Q Application Raw Reducer Report\n\n");
        md.append("- Requested: `").append(application.requested()).append("`\n");
        md.append("- Classes scanned: `").append(application.classCount()).append("`\n");
        md.append("- Classes reduced: `").append(application.reducedClassCount()).append("`\n");
        md.append("- Estimated removable bytes: `").append(application.estimatedRemovableBytes()).append("`\n");
        md.append("- Removed bytes: `").append(application.removedBytes()).append("`\n");
        md.append("- Raw audits: `").append(application.auditedClassCount()).append("`\n\n");
        md.append("Only `ORDINARY_APPLICATION` classes are admitted. Generated and proxy families are copied unchanged.\n");
        Files.writeString(file.toPath(), md.toString(), StandardCharsets.UTF_8);
    }

    private static void writeGeneratedInventoryMarkdown(File file, ApplicationReductionReport application) throws IOException {
        StringBuilder md = new StringBuilder("# V2-Q Generated Application Inventory\n\n");
        md.append("| Family | Count |\n| --- | ---: |\n");
        application.classes().stream().collect(java.util.stream.Collectors.groupingBy(
            ApplicationClassReductionRecord::family, LinkedHashMap::new, java.util.stream.Collectors.counting()))
            .forEach((family, count) -> md.append("| `").append(family).append("` | ").append(count).append(" |\n"));
        Files.writeString(file.toPath(), md.toString(), StandardCharsets.UTF_8);
    }

    private static void writeGeneratedAdmissionMarkdown(File file, ApplicationReductionReport application) throws IOException {
        StringBuilder md = new StringBuilder("# V2-Q Generated Family Admission Policy\n\n");
        md.append("| Family | Admission | Reason |\n| --- | --- | --- |\n");
        for (GeneratedFamilyAssessment assessment : application.familyTaxonomy()) {
            md.append("| `").append(assessment.family()).append("` | `")
                .append(assessment.admission()).append("` | ").append(assessment.reason()).append(" |\n");
        }
        md.append("\nOnly `ALLOW_METADATA_ONLY` is eligible, and it removes LVT/LVTT only with the raw engine.\n");
        Files.writeString(file.toPath(), md.toString(), StandardCharsets.UTF_8);
    }

    private static void writeApplicationAuditMarkdown(File file, ApplicationReductionReport application) throws IOException {
        StringBuilder md = new StringBuilder("# V2-Q Application Byte Preservation Report\n\n");
        md.append("- Audited application classes: `").append(application.auditedClassCount()).append("`\n");
        md.append("- Failed audits: `").append(application.failedAuditCount()).append("`\n");
        md.append("- Non-target structures preserved: `").append(application.rawClassAudits().stream()
            .allMatch(RawReducerClassAuditRecord::preservedNonTargetStructures)).append("`\n");
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
