package com.yourorg.jmoa.plugin.generated;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeneratedClassInventoryReportWriter {

    public void write(File outputDirectory, GeneratedClassInventory inventory) throws IOException {
        if (outputDirectory != null) {
            outputDirectory.mkdirs();
        }
        writeJson(new File(outputDirectory, "generated-class-inventory.json"), inventory);
        writeFamilyBreakdown(new File(outputDirectory, "generated-class-family-breakdown.json"), inventory.familyBreakdown());
        writeMarkdown(new File(outputDirectory, "generated-class-inventory.md"), inventory);
        writeCsv(new File(outputDirectory, "generated-class-inventory-summary.csv"), inventory.familyBreakdown());
    }

    private void writeJson(File target, GeneratedClassInventory inventory) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(target, inventory);
    }

    private void writeFamilyBreakdown(File target, List<GeneratedClassFamilySummary> familyBreakdown) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("families", familyBreakdown);
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(target, root);
    }

    private void writeMarkdown(File target, GeneratedClassInventory inventory) throws IOException {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Generated Class Inventory\n\n");
        markdown.append("- Metadata version: `").append(inventory.metadataVersion()).append("`\n");
        markdown.append("- Generated at: `").append(inventory.generatedAt()).append("`\n");
        markdown.append("- Classes scanned: `").append(inventory.totalClassesScanned()).append("`\n");
        markdown.append("- Generated-like classes: `").append(inventory.generatedLikeClasses()).append("`\n");
        markdown.append("- Classfile bytes: `").append(inventory.totalClassFileBytes()).append("`\n\n");
        markdown.append("## Family Breakdown\n\n");
        markdown.append("| Family | Classes | Generated-like | Bytes | Methods | Synthetic methods | Bridge methods | Lambda methods | Invokedynamic |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (GeneratedClassFamilySummary summary : inventory.familyBreakdown()) {
            markdown.append("| ")
                .append(summary.family())
                .append(" | ")
                .append(summary.classCount())
                .append(" | ")
                .append(summary.generatedLikeClassCount())
                .append(" | ")
                .append(summary.classFileBytes())
                .append(" | ")
                .append(summary.methodCount())
                .append(" | ")
                .append(summary.syntheticMethodCount())
                .append(" | ")
                .append(summary.bridgeMethodCount())
                .append(" | ")
                .append(summary.lambdaMethodCount())
                .append(" | ")
                .append(summary.invokedynamicCount())
                .append(" |\n");
        }
        markdown.append("\n## Safety Boundary\n\n");
        markdown.append("V2-A1 is inventory-only. All generated-class records default to `UNKNOWN` risk unless a later safety phase proves otherwise.\n");
        Files.writeString(target.toPath(), markdown.toString(), StandardCharsets.UTF_8);
    }

    private void writeCsv(File target, List<GeneratedClassFamilySummary> familyBreakdown) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("family,classCount,generatedLikeClassCount,classFileBytes,methodCount,syntheticMethodCount,bridgeMethodCount,lambdaMethodCount,invokedynamicCount\n");
        for (GeneratedClassFamilySummary summary : familyBreakdown) {
            csv.append(summary.family()).append(',')
                .append(summary.classCount()).append(',')
                .append(summary.generatedLikeClassCount()).append(',')
                .append(summary.classFileBytes()).append(',')
                .append(summary.methodCount()).append(',')
                .append(summary.syntheticMethodCount()).append(',')
                .append(summary.bridgeMethodCount()).append(',')
                .append(summary.lambdaMethodCount()).append(',')
                .append(summary.invokedynamicCount()).append('\n');
        }
        Files.writeString(target.toPath(), csv.toString(), StandardCharsets.UTF_8);
    }
}
