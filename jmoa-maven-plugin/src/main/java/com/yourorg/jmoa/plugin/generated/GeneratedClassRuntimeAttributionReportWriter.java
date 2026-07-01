package com.yourorg.jmoa.plugin.generated;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GeneratedClassRuntimeAttributionReportWriter {

    public void write(File outputDirectory, GeneratedClassRuntimeAttribution attribution) throws IOException {
        if (outputDirectory != null) {
            outputDirectory.mkdirs();
        }
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(outputDirectory, "generated-class-runtime-attribution.json"), attribution);
        mapper.writeValue(new File(outputDirectory, "generated-class-origin-map.json"), originMap(attribution));
        writeMarkdown(new File(outputDirectory, "generated-class-runtime-attribution.md"), attribution);
        writeSurvivalReport(new File(outputDirectory, "generated-class-survival-report.md"), attribution);
    }

    private Map<String, Object> originMap(GeneratedClassRuntimeAttribution attribution) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", attribution.metadataVersion());
        root.put("generatedAt", attribution.generatedAt());
        Map<String, Map<String, Object>> origins = new LinkedHashMap<>();
        for (GeneratedClassRuntimeClassRecord record : attribution.classes()) {
            if (record.runtimeLoaded()) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("family", record.family());
                value.put("loadOrigin", record.loadOrigin());
                value.put("staticInventoryPresent", record.staticInventoryPresent());
                value.put("histogramInstances", record.histogramInstances());
                value.put("histogramBytes", record.histogramBytes());
                origins.put(record.className(), value);
            }
        }
        root.put("origins", origins);
        return root;
    }

    private void writeMarkdown(File target, GeneratedClassRuntimeAttribution attribution) throws IOException {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Generated Class Runtime Attribution\n\n");
        markdown.append("- Metadata version: `").append(attribution.metadataVersion()).append("`\n");
        markdown.append("- Generated at: `").append(attribution.generatedAt()).append("`\n");
        markdown.append("- Class-load log: `").append(nullToMissing(attribution.classLoadLog())).append("`\n");
        markdown.append("- Class histogram: `").append(nullToMissing(attribution.classHistogram())).append("`\n");
        markdown.append("- Runtime loaded classes: `").append(attribution.totalRuntimeLoadedClasses()).append("`\n");
        markdown.append("- Generated-like runtime loaded classes: `")
            .append(attribution.totalGeneratedRuntimeLoadedClasses()).append("`\n");
        markdown.append("- Total histogram bytes: `").append(attribution.totalHistogramBytes()).append("`\n\n");
        markdown.append("| Family | Static classes | Loaded | Runtime-only loaded | Histogram classes | Histogram bytes | Survival | Priority |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |\n");
        for (GeneratedClassFamilyRuntimeAttribution family : attribution.families()) {
            markdown.append("| ")
                .append(family.family())
                .append(" | ")
                .append(family.staticClassCount())
                .append(" | ")
                .append(family.runtimeLoadedCount())
                .append(" | ")
                .append(family.runtimeOnlyLoadedCount())
                .append(" | ")
                .append(family.histogramClassCount())
                .append(" | ")
                .append(family.histogramBytes())
                .append(" | ")
                .append(family.survival())
                .append(" | ")
                .append(family.optimizationPriority())
                .append(" |\n");
        }
        markdown.append("\n## Interpretation Boundary\n\n");
        markdown.append("This report attributes generated-class runtime presence and object histogram bytes. ");
        markdown.append("It does not prove a family is safe to transform; safety is handled by the V2-A taxonomy.\n");
        Files.writeString(target.toPath(), markdown.toString(), StandardCharsets.UTF_8);
    }

    private void writeSurvivalReport(File target, GeneratedClassRuntimeAttribution attribution) throws IOException {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Generated Class Survival Report\n\n");
        markdown.append("| Family | Survival | Loaded | Unloaded | Live histogram classes | Live bytes |\n");
        markdown.append("| --- | --- | ---: | ---: | ---: | ---: |\n");
        for (GeneratedClassFamilyRuntimeAttribution family : attribution.families()) {
            markdown.append("| ")
                .append(family.family())
                .append(" | ")
                .append(family.survival())
                .append(" | ")
                .append(family.runtimeLoadedCount())
                .append(" | ")
                .append(family.runtimeUnloadedCount())
                .append(" | ")
                .append(family.histogramClassCount())
                .append(" | ")
                .append(family.histogramBytes())
                .append(" |\n");
        }
        Files.writeString(target.toPath(), markdown.toString(), StandardCharsets.UTF_8);
    }

    private static String nullToMissing(String value) {
        return value == null ? "not-provided" : value;
    }
}
