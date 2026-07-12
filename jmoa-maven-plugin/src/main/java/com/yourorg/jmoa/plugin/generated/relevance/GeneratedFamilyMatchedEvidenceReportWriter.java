package com.yourorg.jmoa.plugin.generated.relevance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the V2-T machine-readable report with a compact human audit companion. */
public final class GeneratedFamilyMatchedEvidenceReportWriter {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(Path outputDirectory, GeneratedFamilyMatchedEvidenceAnalyzer.Report report) throws Exception {
        Files.createDirectories(outputDirectory);
        mapper.writeValue(outputDirectory.resolve("v2t-generated-family-matched-evidence.json").toFile(), report);
        Files.writeString(outputDirectory.resolve("v2t-generated-family-matched-evidence.md"), markdown(report), StandardCharsets.UTF_8);
    }

    private static String markdown(GeneratedFamilyMatchedEvidenceAnalyzer.Report report) {
        StringBuilder text = new StringBuilder("# V2-T Generated-Family Matched Evidence\n\n")
            .append("- Service: `").append(report.service()).append("`\n")
            .append("- Evidence status: `").append(report.evidenceStatus()).append("`\n")
            .append("- Artifact fingerprints match: `").append(report.artifactFingerprintMatch()).append("`\n")
            .append("- Unique generated-like classes: `").append(report.uniqueGeneratedClasses()).append("`\n")
            .append("- Unique generated-like classfile bytes: `").append(report.uniqueGeneratedClassfileBytes()).append("`\n")
            .append("- Prototype admitted: `").append(report.prototypeAdmitted()).append("`\n\n")
            .append("## Exclusive Primary-Family Census\n\n")
            .append("| Family | Unique classes | Unique bytes | Overlap signals |\n| --- | ---: | ---: | ---: |\n");
        for (var row : report.exclusivePrimaryFamilyCensus()) {
            text.append("| ").append(row.primaryFamily()).append(" | ").append(row.uniqueClasses())
                .append(" | ").append(row.uniqueClassfileBytes()).append(" | ")
                .append(row.overlappingClassificationClasses()).append(" |\n");
        }
        text.append("\n## Lifecycle\n\n")
            .append("| Family | Startup | Warmup | Workload | Runtime-only | Classification |\n")
            .append("| --- | ---: | ---: | ---: | ---: | --- |\n");
        for (var row : report.lifecycle()) {
            text.append("| ").append(row.family()).append(" | ").append(row.startupLoaded())
                .append(" | ").append(row.warmupLoaded()).append(" | ").append(row.workloadLoaded())
                .append(" | ").append(row.runtimeOnlyClasses()).append(" | ").append(row.classification()).append(" |\n");
        }
        text.append("\n## Boundaries\n\n");
        for (String boundary : report.boundaries()) {
            text.append("- ").append(boundary).append("\n");
        }
        return text.toString();
    }
}
