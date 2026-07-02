package com.yourorg.jmoa.plugin.attribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.MemoryAttributionReport;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.MemoryCategoryDelta;
import com.yourorg.jmoa.plugin.attribution.AttributionModels.ObjectFamilyDelta;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class MemoryAttributionReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(File outputDirectory, MemoryAttributionReport report) throws IOException {
        Files.createDirectories(outputDirectory.toPath());
        MAPPER.writeValue(new File(outputDirectory, "jmoa-memory-attribution.json"), report);
        MAPPER.writeValue(new File(outputDirectory, "jmoa-category-deltas.json"), report.categoryDeltas());
        MAPPER.writeValue(new File(outputDirectory, "jmoa-smaps-nmt-reconciliation.json"), report.smapsNmtReconciliation());
        MAPPER.writeValue(new File(outputDirectory, "jmoa-heap-object-attribution.json"), report.heapObjectAttribution());
        MAPPER.writeValue(new File(outputDirectory, "jmoa-causal-hypotheses.json"), report.causalHypotheses());
        writeMarkdown(new File(outputDirectory, "jmoa-memory-attribution.md"), report);
        writeObjectFamilyCsv(new File(outputDirectory, "jmoa-top-object-deltas.csv"), report);
    }

    private static void writeMarkdown(File file, MemoryAttributionReport report) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# JMOA Memory Attribution\n\n");
        md.append("- Metadata version: `").append(report.metadataVersion()).append("`\n");
        md.append("- Evidence verdict: `").append(report.evidenceVerdict()).append("`\n");
        md.append("- V2-C valid: `").append(report.v2cValid()).append("`\n");
        if (report.service() != null) {
            md.append("- Service: `").append(report.service()).append("`\n");
        }
        if (report.phase() != null) {
            md.append("- Phase: `").append(report.phase()).append("`\n");
        }
        md.append("\n## Primary Category Deltas\n\n");
        md.append("| Category | Median delta | Unit |\n");
        md.append("| --- | ---: | --- |\n");
        for (MemoryCategoryDelta delta : report.categoryDeltas()) {
            md.append("| `").append(delta.category()).append("` | ")
                .append(delta.medianDelta()).append(" | ")
                .append(delta.unit()).append(" |\n");
        }
        md.append("\n## smaps / NMT Reconciliation\n\n");
        md.append("- Classification: `").append(report.smapsNmtReconciliation().classification()).append("`\n");
        md.append("- PSS delta KB: `").append(report.smapsNmtReconciliation().medianPssDeltaKb()).append("`\n");
        md.append("- Private_Dirty delta KB: `").append(report.smapsNmtReconciliation().medianPrivateDirtyDeltaKb()).append("`\n");
        md.append("- NMT total committed delta KB: `").append(report.smapsNmtReconciliation().medianNmtTotalCommittedDeltaKb()).append("`\n");
        md.append("- NMT-to-PSS gap KB: `").append(report.smapsNmtReconciliation().nmtToPssGapKb()).append("`\n\n");
        for (String reason : report.smapsNmtReconciliation().reasons()) {
            md.append("- ").append(reason).append("\n");
        }
        md.append("\n## Heap / Object Attribution\n\n");
        md.append("- Classification: `").append(report.heapObjectAttribution().classification()).append("`\n");
        md.append("- Heap PSS delta KB: `").append(report.heapObjectAttribution().medianHeapPssDeltaKb()).append("`\n");
        md.append("- Heap used delta KB: `").append(report.heapObjectAttribution().medianHeapUsedDeltaKb()).append("`\n");
        md.append("- Class histogram bytes delta: `").append(report.heapObjectAttribution().medianClassHistogramBytesDelta()).append("`\n\n");
        md.append("| Object family | Instance delta | Byte delta |\n");
        md.append("| --- | ---: | ---: |\n");
        for (ObjectFamilyDelta delta : report.heapObjectAttribution().familyDeltas()) {
            md.append("| `").append(delta.family()).append("` | ")
                .append(delta.medianInstanceDelta()).append(" | ")
                .append(delta.medianByteDelta()).append(" |\n");
        }
        md.append("\n## Class / Metaspace Attribution\n\n");
        md.append("- Class histogram class-count delta: `")
            .append(report.classMetaspaceAttribution().medianClassHistogramClassCountDelta()).append("`\n");
        md.append("- Metaspace committed delta KB: `")
            .append(report.classMetaspaceAttribution().medianMetaspaceCommittedDeltaKb()).append("`\n");
        md.append("- Class committed delta KB: `")
            .append(report.classMetaspaceAttribution().medianClassCommittedDeltaKb()).append("`\n");
        md.append("- Interpretation: ").append(report.classMetaspaceAttribution().interpretation()).append("\n\n");
        md.append("## Generated-Family Attribution\n\n");
        md.append("- Present: `").append(report.generatedFamilyAttribution().present()).append("`\n");
        md.append("- Interpretation: ").append(report.generatedFamilyAttribution().interpretation()).append("\n");
        if (!report.generatedFamilyAttribution().families().isEmpty()) {
            md.append("\n| Family | Static classes | Generated-like classes | Classfile bytes | Runtime loaded | Survivors | Histogram bytes |\n");
            md.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            for (var family : report.generatedFamilyAttribution().families()) {
                md.append("| `").append(family.family()).append("` | ")
                    .append(family.staticClassCount()).append(" | ")
                    .append(family.generatedLikeClassCount()).append(" | ")
                    .append(family.classfileBytes()).append(" | ")
                    .append(family.runtimeLoadedClassCount()).append(" | ")
                    .append(family.workloadSurvivorClassCount()).append(" | ")
                    .append(family.histogramBytes()).append(" |\n");
            }
        }
        md.append("\n## Bytecode Runtime Attribution\n\n");
        md.append("- Present: `").append(report.bytecodeRuntimeAttribution().present()).append("`\n");
        md.append("- Interpretation: ").append(report.bytecodeRuntimeAttribution().interpretation()).append("\n");
        if (report.bytecodeRuntimeAttribution().present()) {
            md.append("- Profile classes: `").append(report.bytecodeRuntimeAttribution().totalProfileClasses()).append("`\n");
            md.append("- Runtime loaded classes observed: `")
                .append(report.bytecodeRuntimeAttribution().totalRuntimeLoadedClasses()).append("`\n");
            md.append("- Profile classes observed loaded: `")
                .append(report.bytecodeRuntimeAttribution().profileClassesObservedLoaded()).append("`\n");
            md.append("- Profile classes with histogram instances: `")
                .append(report.bytecodeRuntimeAttribution().profileClassesWithHistogramInstances()).append("`\n");
            md.append("- Near-64KB methods: `").append(report.bytecodeRuntimeAttribution().near64kMethods()).append("`\n");
            md.append("- Near-64KB loaded methods: `")
                .append(report.bytecodeRuntimeAttribution().near64kRuntimeLoadedMethods()).append("`\n");
        }
        md.append("\n");
        md.append("## Causal Hypotheses\n\n");
        for (var hypothesis : report.causalHypotheses()) {
            md.append("### `").append(hypothesis.hypothesis()).append("`\n\n");
            md.append("- Confidence: `").append(hypothesis.confidence()).append("`\n");
            md.append("- Next action: ").append(hypothesis.nextAction()).append("\n\n");
            md.append("Evidence:\n\n");
            for (String evidence : hypothesis.evidence()) {
                md.append("- ").append(evidence).append("\n");
            }
            if (!hypothesis.notEvidence().isEmpty()) {
                md.append("\nNot evidence:\n\n");
                for (String item : hypothesis.notEvidence()) {
                    md.append("- ").append(item).append("\n");
                }
            }
            md.append("\n");
        }
        md.append("## Boundary\n\n");
        for (String boundary : report.boundaries()) {
            md.append("- ").append(boundary).append("\n");
        }
        Files.writeString(file.toPath(), md.toString(), StandardCharsets.UTF_8);
    }

    private static void writeObjectFamilyCsv(File file, MemoryAttributionReport report) throws IOException {
        StringBuilder csv = new StringBuilder("family,medianInstanceDelta,medianByteDelta\n");
        for (ObjectFamilyDelta delta : report.heapObjectAttribution().familyDeltas()) {
            csv.append(delta.family()).append(',')
                .append(delta.medianInstanceDelta()).append(',')
                .append(delta.medianByteDelta()).append('\n');
        }
        Files.writeString(file.toPath(), csv.toString(), StandardCharsets.UTF_8);
    }
}
