package com.yourorg.jmoa.plugin.generated;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class GeneratedClassRoiV2ReportWriter {

    public GeneratedClassRoiV2Report build(
        GeneratedClassInventory inventory,
        GeneratedClassRuntimeAttribution runtimeAttribution,
        GeneratedClassSafetyTaxonomy safetyTaxonomy
    ) {
        Map<GeneratedClassFamily, FamilyAccumulator> accumulators = new EnumMap<>(GeneratedClassFamily.class);
        if (inventory != null) {
            for (GeneratedClassFamilySummary summary : inventory.familyBreakdown()) {
                FamilyAccumulator acc = accumulators.computeIfAbsent(summary.family(), ignored -> new FamilyAccumulator());
                acc.generatedClassCount = summary.generatedLikeClassCount();
                acc.generatedClassBytes = summary.classFileBytes();
                acc.syntheticMethodCount = summary.syntheticMethodCount();
                acc.bridgeMethodCount = summary.bridgeMethodCount();
            }
        }
        if (runtimeAttribution != null) {
            for (GeneratedClassFamilyRuntimeAttribution family : runtimeAttribution.families()) {
                FamilyAccumulator acc = accumulators.computeIfAbsent(family.family(), ignored -> new FamilyAccumulator());
                acc.runtimeGeneratedClassCount = family.runtimeLoadedCount();
                acc.histogramBytes = family.histogramBytes();
                acc.optimizationPriority = family.optimizationPriority();
            }
        }
        if (safetyTaxonomy != null) {
            Map<GeneratedClassFamily, GeneratedClassSafetyCategory> byFamily = new EnumMap<>(GeneratedClassFamily.class);
            for (GeneratedClassTransformEligibility item : safetyTaxonomy.eligibility()) {
                byFamily.merge(item.family(), item.safetyCategory(), GeneratedClassRoiV2ReportWriter::moreConservative);
            }
            for (Map.Entry<GeneratedClassFamily, GeneratedClassSafetyCategory> entry : byFamily.entrySet()) {
                FamilyAccumulator acc = accumulators.computeIfAbsent(entry.getKey(), ignored -> new FamilyAccumulator());
                acc.safetyRisk = entry.getValue().name();
            }
        }
        List<GeneratedClassRoiV2FamilyFeature> families = new ArrayList<>();
        for (Map.Entry<GeneratedClassFamily, FamilyAccumulator> entry : accumulators.entrySet()) {
            FamilyAccumulator acc = entry.getValue();
            families.add(new GeneratedClassRoiV2FamilyFeature(
                entry.getKey(),
                acc.generatedClassCount,
                acc.generatedClassBytes,
                acc.syntheticMethodCount,
                acc.bridgeMethodCount,
                acc.runtimeGeneratedClassCount,
                acc.histogramBytes,
                acc.safetyRisk == null ? "UNKNOWN" : acc.safetyRisk,
                acc.optimizationPriority == null ? "UNMEASURED" : acc.optimizationPriority
            ));
        }
        families = families.stream()
            .sorted(Comparator.comparing(GeneratedClassRoiV2FamilyFeature::family))
            .toList();
        return new GeneratedClassRoiV2Report(
            "v2-a7-roi-feature-report",
            Instant.now().toString(),
            "SYNTHETIC_INVENTORY_ONLY",
            families
        );
    }

    public void write(
        File outputDirectory,
        GeneratedClassInventory inventory,
        GeneratedClassRuntimeAttribution runtimeAttribution,
        GeneratedClassSafetyTaxonomy safetyTaxonomy
    ) throws IOException {
        if (outputDirectory != null) {
            outputDirectory.mkdirs();
        }
        GeneratedClassRoiV2Report report = build(inventory, runtimeAttribution, safetyTaxonomy);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(outputDirectory, "jmoa-roi-v2-report.json"), report);
        writeMarkdown(new File(outputDirectory, "jmoa-roi-v2-report.md"), report);
    }

    private void writeMarkdown(File target, GeneratedClassRoiV2Report report) throws IOException {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# JMOA ROI V2 Generated-Class Feature Report\n\n");
        markdown.append("- Metadata version: `").append(report.metadataVersion()).append("`\n");
        markdown.append("- Profile: `").append(report.profile()).append("`\n\n");
        markdown.append("| Family | Static generated classes | Bytes | Synthetic methods | Bridge methods | Runtime loaded | Histogram bytes | Safety | Priority |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |\n");
        for (GeneratedClassRoiV2FamilyFeature family : report.families()) {
            markdown.append("| ")
                .append(family.family())
                .append(" | ")
                .append(family.generatedClassCount())
                .append(" | ")
                .append(family.generatedClassBytes())
                .append(" | ")
                .append(family.syntheticMethodCount())
                .append(" | ")
                .append(family.bridgeMethodCount())
                .append(" | ")
                .append(family.runtimeGeneratedClassCount())
                .append(" | ")
                .append(family.histogramBytes())
                .append(" | ")
                .append(family.safetyRisk())
                .append(" | ")
                .append(family.optimizationPriority())
                .append(" |\n");
        }
        markdown.append("\nThis report feeds generated-class economics into JMOA's ROI model without admitting unsafe transforms.\n");
        Files.writeString(target.toPath(), markdown.toString(), StandardCharsets.UTF_8);
    }

    private static GeneratedClassSafetyCategory moreConservative(
        GeneratedClassSafetyCategory left,
        GeneratedClassSafetyCategory right
    ) {
        return score(right) > score(left) ? right : left;
    }

    private static int score(GeneratedClassSafetyCategory category) {
        return switch (category) {
            case UNSAFE_RUNTIME_SEMANTIC -> 5;
            case UNKNOWN -> 4;
            case SAFE_TO_DEFER_TO_CDS -> 3;
            case SAFE_TO_REPACK_ONLY -> 2;
            case SAFE_TO_MOVE_TO_AOT, SAFE_TO_CONSOLIDATE, SAFE_TO_REPLACE_WITH_SHARED_ADAPTER -> 1;
        };
    }

    private static final class FamilyAccumulator {
        int generatedClassCount;
        long generatedClassBytes;
        int syntheticMethodCount;
        int bridgeMethodCount;
        int runtimeGeneratedClassCount;
        long histogramBytes;
        String safetyRisk;
        String optimizationPriority;
    }
}
