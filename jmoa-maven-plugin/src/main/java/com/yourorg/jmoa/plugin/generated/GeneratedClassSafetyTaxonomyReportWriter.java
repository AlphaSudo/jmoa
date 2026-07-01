package com.yourorg.jmoa.plugin.generated;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GeneratedClassSafetyTaxonomyReportWriter {

    public void write(File outputDirectory, GeneratedClassSafetyTaxonomy taxonomy) throws IOException {
        if (outputDirectory != null) {
            outputDirectory.mkdirs();
        }
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(outputDirectory, "generated-class-safety-taxonomy.json"), taxonomy);
        mapper.writeValue(new File(outputDirectory, "generated-class-transform-eligibility.json"), transformEligibility(taxonomy));
        writeMarkdown(new File(outputDirectory, "generated-class-safety-taxonomy.md"), taxonomy);
    }

    private Map<String, Object> transformEligibility(GeneratedClassSafetyTaxonomy taxonomy) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", taxonomy.metadataVersion());
        root.put("generatedAt", taxonomy.generatedAt());
        root.put("eligibility", taxonomy.eligibility());
        return root;
    }

    private void writeMarkdown(File target, GeneratedClassSafetyTaxonomy taxonomy) throws IOException {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Generated Class Safety Taxonomy\n\n");
        markdown.append("- Metadata version: `").append(taxonomy.metadataVersion()).append("`\n");
        markdown.append("- Generated at: `").append(taxonomy.generatedAt()).append("`\n");
        markdown.append("- Classified generated-like classes: `").append(taxonomy.totalClasses()).append("`\n\n");
        markdown.append("## Category Counts\n\n");
        markdown.append("| Category | Classes |\n");
        markdown.append("| --- | ---: |\n");
        for (Map.Entry<GeneratedClassSafetyCategory, Integer> entry : taxonomy.categoryCounts().entrySet()) {
            markdown.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |\n");
        }
        markdown.append("\n## Policy\n\n");
        markdown.append("`UNKNOWN` remains the default safety state. Runtime proxy families are report-only. ");
        markdown.append("Spring AOT helpers are admitted for repack/origin verification only; bytecode consolidation requires later semantic gates.\n\n");
        markdown.append("## Transform Eligibility\n\n");
        markdown.append("| Class | Family | Safety | Allowed | Forbidden |\n");
        markdown.append("| --- | --- | --- | --- | --- |\n");
        for (GeneratedClassTransformEligibility item : taxonomy.eligibility()) {
            markdown.append("| `")
                .append(item.className())
                .append("` | ")
                .append(item.family())
                .append(" | ")
                .append(item.safetyCategory())
                .append(" | ")
                .append(String.join(", ", item.allowedTransforms()))
                .append(" | ")
                .append(String.join(", ", item.forbiddenTransforms()))
                .append(" |\n");
        }
        Files.writeString(target.toPath(), markdown.toString(), StandardCharsets.UTF_8);
    }
}
