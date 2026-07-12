package com.yourorg.jmoa.plugin.generated.relevance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the V2-U machine-readable report with a compact human audit companion. */
public final class GeneratedFamilyMatchedEvidenceReportWriter {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(Path outputDirectory, GeneratedFamilyMatchedEvidenceAnalyzer.Report report) throws Exception {
        Files.createDirectories(outputDirectory);
        mapper.writeValue(outputDirectory.resolve("v2u-generated-family-matched-evidence.json").toFile(), report);
        Files.writeString(outputDirectory.resolve("v2u-generated-family-matched-evidence.md"), markdown(report), StandardCharsets.UTF_8);
        mapper.writeValue(outputDirectory.resolve("v2t-generated-family-matched-evidence.json").toFile(), report);
        Files.writeString(outputDirectory.resolve("v2t-generated-family-matched-evidence.md"), markdown(report), StandardCharsets.UTF_8);
    }

    private static String markdown(GeneratedFamilyMatchedEvidenceAnalyzer.Report report) {
        StringBuilder text = new StringBuilder("# V2-U Generated-Family Matched Evidence\n\n")
            .append("- Service: `").append(report.service()).append("`\n")
            .append("- Evidence status: `").append(report.evidenceStatus()).append("`\n")
            .append("- Artifact fingerprints match: `").append(report.artifactFingerprintMatch()).append("`\n")
            .append("- Identity tuple match: `").append(report.identityTupleMatch()).append("`\n")
            .append("- Unique generated-like classes: `").append(report.uniqueGeneratedClasses()).append("`\n")
            .append("- Unique generated-like classfile bytes: `").append(report.uniqueGeneratedClassfileBytes()).append("`\n")
            .append("- Prototype admitted: `").append(report.prototypeAdmitted()).append("`\n\n")
            .append("## Identity Checks\n\n")
            .append("| Field | Static | Capture | Present | Matched |\n")
            .append("| --- | --- | --- | --- | --- |\n");
        for (var check : report.identityChecks()) {
            text.append("| ").append(check.field())
                .append(" | `").append(nullToMissing(check.staticValue())).append("`")
                .append(" | `").append(nullToMissing(check.captureValue())).append("`")
                .append(" | `").append(check.present()).append("`")
                .append(" | `").append(check.matched()).append("` |\n");
        }
        text.append("\n")
            .append("## Exclusive Primary-Family Census\n\n")
            .append("| Family | Unique classes | Unique bytes | Overlap signals |\n| --- | ---: | ---: | ---: |\n");
        for (var row : report.exclusivePrimaryFamilyCensus()) {
            text.append("| ").append(row.primaryFamily()).append(" | ").append(row.uniqueClasses())
                .append(" | ").append(row.uniqueClassfileBytes()).append(" | ")
                .append(row.overlappingClassificationClasses()).append(" |\n");
        }
        text.append("\n## Lifecycle\n\n")
            .append("| Family | Packaged | Startup | Warmup | Workload | New warmup | New workload | Runtime-only workload | Histogram classes | Classification |\n")
            .append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (var row : report.lifecycle()) {
            text.append("| ").append(row.family()).append(" | ").append(row.packagedClasses())
                .append(" | ").append(row.startupLoaded())
                .append(" | ").append(row.warmupLoaded()).append(" | ").append(row.workloadLoaded())
                .append(" | ").append(row.newlyLoadedDuringWarmup())
                .append(" | ").append(row.newlyLoadedDuringWorkload())
                .append(" | ").append(row.runtimeOnlyWorkload())
                .append(" | ").append(row.histogramPersistentClasses())
                .append(" | ").append(row.classification()).append(" |\n");
        }
        text.append("\n## Boundaries\n\n");
        for (String boundary : report.boundaries()) {
            text.append("- ").append(boundary).append("\n");
        }
        return text.toString();
    }

    private static String nullToMissing(String value) {
        return value == null || value.isBlank() ? "missing" : value;
    }
}
