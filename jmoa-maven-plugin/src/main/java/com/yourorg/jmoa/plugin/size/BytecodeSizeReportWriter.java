package com.yourorg.jmoa.plugin.size;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BytecodeSizeReportWriter {

    public void write(File outputDirectory, ClassfileSizeProfile profile) throws IOException {
        if (outputDirectory != null) {
            outputDirectory.mkdirs();
        }
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(outputDirectory, "classfile-size-profile.json"), profile);
        mapper.writeValue(new File(outputDirectory, "classfile-size-family-breakdown.json"),
            Map.of("families", profile.familyBreakdown()));
        mapper.writeValue(new File(outputDirectory, "method-code-size-report.json"), methodReport(profile));
        mapper.writeValue(new File(outputDirectory, "near-64kb-methods.json"), nearLimitMethods(profile));
        mapper.writeValue(new File(outputDirectory, "constant-pool-bloat-report.json"), constantPoolReport(profile));
        mapper.writeValue(new File(outputDirectory, "attribute-size-report.json"), attributeReport(profile));
        mapper.writeValue(new File(outputDirectory, "bytecode-roi-v2-report.json"), roiReport(profile));
        writeClassProfileMarkdown(new File(outputDirectory, "classfile-size-profile.md"), profile);
        writeMethodMarkdown(new File(outputDirectory, "method-code-size-report.md"), profile);
        writeCsv(new File(outputDirectory, "classfile-size-profile.csv"), profile);
    }

    private Map<String, Object> methodReport(ClassfileSizeProfile profile) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2-b2-method-code-size-report");
        root.put("generatedAt", Instant.now().toString());
        root.put("methods", profile.methods());
        return root;
    }

    private List<MethodSizeRecord> nearLimitMethods(ClassfileSizeProfile profile) {
        return profile.methods().stream()
            .filter(method -> method.threshold() == MethodSizeRisk.WARN
                || method.threshold() == MethodSizeRisk.DANGER
                || method.threshold() == MethodSizeRisk.CRITICAL
                || method.threshold() == MethodSizeRisk.LIMIT)
            .toList();
    }

    private Map<String, Object> constantPoolReport(ClassfileSizeProfile profile) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2-b3-constant-pool-bloat-report");
        root.put("generatedAt", Instant.now().toString());
        root.put("classes", profile.constantPools());
        return root;
    }

    private Map<String, Object> attributeReport(ClassfileSizeProfile profile) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadataVersion", "v2-b4-attribute-size-report");
        root.put("generatedAt", Instant.now().toString());
        root.put("classes", profile.attributes());
        return root;
    }

    private BytecodeRoiV2Report roiReport(ClassfileSizeProfile profile) {
        List<BytecodeRoiV2Record> candidates = profile.classes().stream()
            .map(record -> new BytecodeRoiV2Record(
                record.className(),
                record.artifact(),
                record.generatedFamily(),
                record.generatedRiskLevel(),
                record.generatedLike(),
                record.classfileBytes(),
                record.largestMethodCodeLength(),
                record.totalMethodCodeBytes(),
                record.constantPoolCount(),
                record.totalAttributeBytes(),
                record.debugAttributeBytes(),
                riskFor(record.largestMethodCodeLength()),
                priorityFor(record)
            ))
            .sorted(Comparator
                .comparing(BytecodeRoiV2Record::candidatePriority)
                .thenComparing(BytecodeRoiV2Record::classfileBytes, Comparator.reverseOrder())
                .thenComparing(BytecodeRoiV2Record::className))
            .toList();
        return new BytecodeRoiV2Report(
            "v2-b5-bytecode-roi-report",
            Instant.now().toString(),
            candidates
        );
    }

    private static MethodSizeRisk riskFor(int codeLength) {
        if (codeLength >= 65_535) return MethodSizeRisk.LIMIT;
        if (codeLength >= 60_000) return MethodSizeRisk.CRITICAL;
        if (codeLength >= 49_152) return MethodSizeRisk.DANGER;
        if (codeLength >= 32_768) return MethodSizeRisk.WARN;
        if (codeLength >= 16_384) return MethodSizeRisk.LARGE;
        if (codeLength >= 8_192) return MethodSizeRisk.NOTICE;
        return MethodSizeRisk.NORMAL;
    }

    private static String priorityFor(ClassfileSizeRecord record) {
        MethodSizeRisk risk = riskFor(record.largestMethodCodeLength());
        if (risk == MethodSizeRisk.LIMIT || risk == MethodSizeRisk.CRITICAL || risk == MethodSizeRisk.DANGER) {
            return record.generatedLike() ? "HIGH_GENERATED_SIZE_RISK" : "HIGH_SIZE_RISK";
        }
        if (record.generatedLike() && (record.classfileBytes() >= 64 * 1024 || record.totalMethodCodeBytes() >= 32 * 1024)) {
            return "MEDIUM_GENERATED_SIZE_RISK";
        }
        if (record.classfileBytes() >= 128 * 1024 || record.debugAttributeBytes() >= 32 * 1024) {
            return "MEDIUM_SIZE_RISK";
        }
        return "LOW";
    }

    private void writeClassProfileMarkdown(File target, ClassfileSizeProfile profile) throws IOException {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Classfile Size Profile\n\n");
        markdown.append("- Metadata version: `").append(profile.metadataVersion()).append("`\n");
        markdown.append("- Generated at: `").append(profile.generatedAt()).append("`\n");
        markdown.append("- Classes scanned: `").append(profile.totalClassesScanned()).append("`\n");
        markdown.append("- Total classfile bytes: `").append(profile.totalClassfileBytes()).append("`\n");
        markdown.append("- Total method code bytes: `").append(profile.totalMethodCodeBytes()).append("`\n");
        markdown.append("- Largest method code length: `").append(profile.largestMethodCodeLength()).append("`\n\n");
        markdown.append("## Family Breakdown\n\n");
        markdown.append("| Generated family | Classes | Generated-like | Bytes | Method bytes | Largest method | Attribute bytes | Debug bytes | Annotation bytes | CP entries |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (ClassfileSizeFamilySummary family : profile.familyBreakdown()) {
            markdown.append("| ")
                .append(family.generatedFamily())
                .append(" | ")
                .append(family.classCount())
                .append(" | ")
                .append(family.generatedLikeClassCount())
                .append(" | ")
                .append(family.classfileBytes())
                .append(" | ")
                .append(family.totalMethodCodeBytes())
                .append(" | ")
                .append(family.largestMethodCodeLength())
                .append(" | ")
                .append(family.totalAttributeBytes())
                .append(" | ")
                .append(family.debugAttributeBytes())
                .append(" | ")
                .append(family.annotationAttributeBytes())
                .append(" | ")
                .append(family.constantPoolEntries())
                .append(" |\n");
        }
        markdown.append("\n## Top Largest Classes\n\n");
        markdown.append("| Class | Artifact | Bytes | Method bytes | Largest method | CP entries | Generated family |\n");
        markdown.append("| --- | --- | ---: | ---: | ---: | ---: | --- |\n");
        for (ClassfileSizeRecord record : profile.classes().stream().limit(50).toList()) {
            markdown.append("| `")
                .append(record.className())
                .append("` | `")
                .append(record.artifact())
                .append("` | ")
                .append(record.classfileBytes())
                .append(" | ")
                .append(record.totalMethodCodeBytes())
                .append(" | ")
                .append(record.largestMethodCodeLength())
                .append(" | ")
                .append(record.constantPoolCount())
                .append(" | ")
                .append(record.generatedFamily())
                .append(" |\n");
        }
        Files.writeString(target.toPath(), markdown.toString(), StandardCharsets.UTF_8);
    }

    private void writeMethodMarkdown(File target, ClassfileSizeProfile profile) throws IOException {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Method Code Size Report\n\n");
        markdown.append("| Class | Method | Code bytes | Risk | Synthetic | Bridge | Static init | Instructions | Invokes | Branches | Switches |\n");
        markdown.append("| --- | --- | ---: | --- | --- | --- | --- | ---: | ---: | ---: | ---: |\n");
        for (MethodSizeRecord method : profile.methods().stream().limit(100).toList()) {
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
                .append(method.synthetic())
                .append(" | ")
                .append(method.bridge())
                .append(" | ")
                .append(method.staticInitializer())
                .append(" | ")
                .append(method.instructionCount())
                .append(" | ")
                .append(method.invokeInstructionCount())
                .append(" | ")
                .append(method.branchInstructionCount())
                .append(" | ")
                .append(method.switchInstructionCount())
                .append(" |\n");
        }
        Files.writeString(target.toPath(), markdown.toString(), StandardCharsets.UTF_8);
    }

    private void writeCsv(File target, ClassfileSizeProfile profile) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("className,artifact,classfileBytes,constantPoolCount,methodCount,totalMethodCodeBytes,largestMethodCodeLength,totalAttributeBytes,debugAttributeBytes,annotationAttributeBytes,generatedFamily,generatedLike\n");
        for (ClassfileSizeRecord record : profile.classes()) {
            csv.append(escape(record.className())).append(',')
                .append(escape(record.artifact())).append(',')
                .append(record.classfileBytes()).append(',')
                .append(record.constantPoolCount()).append(',')
                .append(record.methodCount()).append(',')
                .append(record.totalMethodCodeBytes()).append(',')
                .append(record.largestMethodCodeLength()).append(',')
                .append(record.totalAttributeBytes()).append(',')
                .append(record.debugAttributeBytes()).append(',')
                .append(record.annotationAttributeBytes()).append(',')
                .append(record.generatedFamily()).append(',')
                .append(record.generatedLike())
                .append('\n');
        }
        Files.writeString(target.toPath(), csv.toString(), StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") ? "\"" + escaped + "\"" : escaped;
    }
}
