package com.yourorg.jmoa.plugin.generated.relevance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yourorg.jmoa.plugin.generated.relevance.GeneratedFamilyRelevanceModels.GeneratedFamilyRelevanceReport;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class GeneratedFamilyRelevanceReportWriter {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(File outputDirectory, GeneratedFamilyRelevanceReport report) throws IOException {
        Files.createDirectories(outputDirectory.toPath());
        writeJson(new File(outputDirectory, "v2s-static-family-census.json"), report.staticCensus());
        writeJson(new File(outputDirectory, "v2s-static-runtime-reconciliation.json"), report.reconciliation());
        writeJson(new File(outputDirectory, "v2s-generated-family-safety-matrix.json"), report.safetyMatrix());
        writeJson(new File(outputDirectory, "v2s-generated-family-roi-ranking.json"), report);
        Files.writeString(new File(outputDirectory, "v2s-generated-family-roi-ranking.md").toPath(), markdown(report), StandardCharsets.UTF_8);
    }

    private void writeJson(File file, Object value) throws IOException {
        mapper.writeValue(file, value);
    }

    private static String markdown(GeneratedFamilyRelevanceReport report) {
        StringBuilder value = new StringBuilder("# V2-S Generated-Family Runtime Relevance\n\n");
        value.append("Service: `").append(report.service()).append("`  \nDiagnostic-only: `")
            .append(report.diagnosticOnly()).append("`\n\n");
        value.append("| Family | Static bytes | Loaded classes | Relevance | Recommendation | Score |\n")
            .append("|---|---:|---:|---|---|---:|\n");
        for (var item : report.roiRanking()) {
            value.append('|').append(item.family()).append('|').append(item.staticBytes()).append('|')
                .append(item.runtimeLoadedClasses()).append('|').append(item.relevance()).append('|')
                .append(item.recommendation()).append('|').append(item.totalScore()).append("|\n");
        }
        value.append("\nV2-S does not admit mutation automatically. `CANDIDATE_FOR_PROTOTYPE` requires a later, family-specific semantic and evidence plan.\n");
        return value.toString();
    }
}
