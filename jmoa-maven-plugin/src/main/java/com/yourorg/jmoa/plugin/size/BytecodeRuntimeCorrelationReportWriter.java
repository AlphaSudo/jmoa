package com.yourorg.jmoa.plugin.size;

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

public final class BytecodeRuntimeCorrelationReportWriter {

    public void write(File outputDirectory, BytecodeRuntimeCorrelationReport report) throws IOException {
        if (outputDirectory != null) {
            outputDirectory.mkdirs();
        }
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(outputDirectory, "bytecode-runtime-correlation.json"), report);
        mapper.writeValue(new File(outputDirectory, "bytecode-runtime-correlation-top-loaded.json"), topLoaded(report));
        mapper.writeValue(new File(outputDirectory, "bytecode-runtime-correlation-near64k.json"), near64k(report));
        writeMarkdown(new File(outputDirectory, "bytecode-runtime-correlation.md"), report);
    }

    private Map<String, Object> topLoaded(BytecodeRuntimeCorrelationReport report) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2-b2-bytecode-runtime-correlation-top-loaded");
        root.put("classes", report.classes().stream()
            .filter(BytecodeRuntimeClassCorrelation::runtimeLoaded)
            .sorted(Comparator
                .comparing(BytecodeRuntimeClassCorrelation::classfileBytes, Comparator.reverseOrder())
                .thenComparing(BytecodeRuntimeClassCorrelation::className))
            .limit(100)
            .toList());
        return root;
    }

    private Map<String, Object> near64k(BytecodeRuntimeCorrelationReport report) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2-b2-bytecode-runtime-correlation-near64k");
        root.put("methods", report.methods().stream()
            .filter(method -> method.threshold() == MethodSizeRisk.WARN
                || method.threshold() == MethodSizeRisk.DANGER
                || method.threshold() == MethodSizeRisk.CRITICAL
                || method.threshold() == MethodSizeRisk.LIMIT)
            .toList());
        return root;
    }

    private void writeMarkdown(File target, BytecodeRuntimeCorrelationReport report) throws IOException {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Bytecode Runtime Correlation\n\n");
        markdown.append("- Metadata version: `").append(report.metadataVersion()).append("`\n");
        markdown.append("- Generated at: `").append(report.generatedAt()).append("`\n");
        markdown.append("- Class-load log: `").append(report.classLoadLog() == null ? "not provided" : report.classLoadLog()).append("`\n");
        markdown.append("- Class histogram: `").append(report.classHistogram() == null ? "not provided" : report.classHistogram()).append("`\n");
        markdown.append("- Profile classes: `").append(report.totalProfileClasses()).append("`\n");
        markdown.append("- Runtime loaded classes observed: `").append(report.totalRuntimeLoadedClasses()).append("`\n");
        markdown.append("- Profile classes observed loaded: `").append(report.profileClassesObservedLoaded()).append("`\n");
        markdown.append("- Profile classes with histogram instances: `").append(report.profileClassesWithHistogramInstances()).append("`\n\n");

        markdown.append("## Category Counts\n\n");
        markdown.append("| Category | Classes |\n");
        markdown.append("| --- | ---: |\n");
        for (Map.Entry<RuntimeCorrelationCategory, Integer> entry : report.categoryCounts().entrySet()) {
            markdown.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |\n");
        }

        markdown.append("\n## Family Correlation\n\n");
        markdown.append("| Family | Static classes | Loaded classes | Survivor classes | Classfile bytes | Loaded bytes | Histogram bytes |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (BytecodeRuntimeFamilyCorrelation family : report.families()) {
            markdown.append("| ")
                .append(family.generatedFamily())
                .append(" | ")
                .append(family.staticClassCount())
                .append(" | ")
                .append(family.runtimeLoadedClassCount())
                .append(" | ")
                .append(family.workloadSurvivorClassCount())
                .append(" | ")
                .append(family.classfileBytes())
                .append(" | ")
                .append(family.loadedClassfileBytes())
                .append(" | ")
                .append(family.histogramBytes())
                .append(" |\n");
        }

        markdown.append("\n## Top Loaded Size Risks\n\n");
        markdown.append("| Class | Artifact | Bytes | Largest method | Histogram bytes | Category | Priority |\n");
        markdown.append("| --- | --- | ---: | ---: | ---: | --- | --- |\n");
        for (BytecodeRuntimeClassCorrelation record : report.classes().stream()
            .filter(BytecodeRuntimeClassCorrelation::runtimeLoaded)
            .sorted(Comparator
                .comparing(BytecodeRuntimeClassCorrelation::classfileBytes, Comparator.reverseOrder())
                .thenComparing(BytecodeRuntimeClassCorrelation::className))
            .limit(50)
            .toList()) {
            markdown.append("| `")
                .append(record.className())
                .append("` | `")
                .append(record.artifact())
                .append("` | ")
                .append(record.classfileBytes())
                .append(" | ")
                .append(record.largestMethodCodeLength())
                .append(" | ")
                .append(record.histogramBytes())
                .append(" | ")
                .append(record.category())
                .append(" | ")
                .append(record.priority())
                .append(" |\n");
        }

        markdown.append("\n## Near-64KB Methods With Runtime Status\n\n");
        markdown.append("| Class | Method | Code bytes | Risk | Loaded class | Histogram bytes | Category |\n");
        markdown.append("| --- | --- | ---: | --- | --- | ---: | --- |\n");
        for (BytecodeRuntimeMethodCorrelation method : report.methods().stream()
            .filter(item -> item.threshold() == MethodSizeRisk.WARN
                || item.threshold() == MethodSizeRisk.DANGER
                || item.threshold() == MethodSizeRisk.CRITICAL
                || item.threshold() == MethodSizeRisk.LIMIT)
            .limit(50)
            .toList()) {
            markdown.append("| `")
                .append(method.className())
                .append("` | `")
                .append(method.methodName())
                .append(method.descriptor())
                .append("` | ")
                .append(method.codeLength())
                .append(" | ")
                .append(method.threshold())
                .append(" | ")
                .append(method.runtimeLoadedClass())
                .append(" | ")
                .append(method.classHistogramBytes())
                .append(" | ")
                .append(method.classCategory())
                .append(" |\n");
        }

        markdown.append("\n## Boundary\n\n");
        markdown.append("This report correlates static bytecode footprint with class-load and histogram evidence. ");
        markdown.append("It does not prove causality for PSS, Private_Dirty, or startup without a paired runtime experiment.\n");
        Files.writeString(target.toPath(), markdown.toString(), StandardCharsets.UTF_8);
    }
}

