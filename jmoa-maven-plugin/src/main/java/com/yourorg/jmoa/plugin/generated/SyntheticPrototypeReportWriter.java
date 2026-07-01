package com.yourorg.jmoa.plugin.generated;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class SyntheticPrototypeReportWriter {

    private static final String SELECTION_VERSION = "v2-a4-prototype-family-selection";
    private static final String PROTOTYPE_VERSION = "v2-a5-synthetic-optimizer-prototype";

    public void write(File outputDirectory, GeneratedClassInventory inventory) throws IOException {
        if (outputDirectory != null) {
            outputDirectory.mkdirs();
        }
        SyntheticPrototypeFamilySelection selection = selection();
        SyntheticOptimizerPrototypeReport prototype = prototype(inventory);
        SyntheticSafetyValidationReport validation = validation();

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(outputDirectory, "synthetic-prototype-family-selection.json"), selection);
        mapper.writeValue(new File(outputDirectory, "synthetic-optimizer-prototype-report.json"), prototype);
        mapper.writeValue(new File(outputDirectory, "synthetic-affected-classes.json"), prototype.affectedClasses());
        mapper.writeValue(new File(outputDirectory, "synthetic-rewritten-classes.json"), List.of());
        mapper.writeValue(new File(outputDirectory, "synthetic-safety-validation.json"), validation);
        writeSelectionMarkdown(new File(outputDirectory, "synthetic-prototype-family-selection.md"), selection);
        writePrototypeMarkdown(new File(outputDirectory, "synthetic-optimizer-prototype-report.md"), prototype, validation);
    }

    private SyntheticPrototypeFamilySelection selection() {
        return new SyntheticPrototypeFamilySelection(
            SELECTION_VERSION,
            Instant.now().toString(),
            GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS,
            "REPORT_ONLY_REPACK_CANDIDATE",
            false,
            List.of(
                "Spring AOT BeanDefinition helpers are build-time generated and statically inventoryable.",
                "They are less runtime identity-sensitive than CGLIB, JDK proxy, ByteBuddy, or Hibernate proxy classes.",
                "The first implementation is intentionally report-only until bean-count and endpoint behavior gates are automated."
            ),
            List.of(
                GeneratedClassFamily.SPRING_CGLIB,
                GeneratedClassFamily.JDK_PROXY,
                GeneratedClassFamily.BYTEBUDDY,
                GeneratedClassFamily.HIBERNATE_PROXY,
                GeneratedClassFamily.SPRING_DATA_GENERATED
            ),
            List.of(
                "Bytecode verifies",
                "ApplicationContext starts",
                "Bean definition count unchanged",
                "Repository count unchanged when applicable",
                "Health endpoint UP",
                "Business workload returns 0 errors",
                "Runtime origins verified",
                "PSS/Private_Dirty not worse in a screen"
            )
        );
    }

    private SyntheticOptimizerPrototypeReport prototype(GeneratedClassInventory inventory) {
        List<GeneratedClassRecord> affected = inventory == null ? List.of() : inventory.classes().stream()
            .filter(record -> record.family() == GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS)
            .sorted(Comparator.comparing(GeneratedClassRecord::className))
            .toList();
        return new SyntheticOptimizerPrototypeReport(
            PROTOTYPE_VERSION,
            Instant.now().toString(),
            GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS,
            "REPORT_ONLY",
            false,
            affected.size(),
            affected.stream().mapToLong(GeneratedClassRecord::classFileBytes).sum(),
            affected,
            List.of("REPACK_ONLY", "RUNTIME_ORIGIN_VERIFY", "DUPLICATE_HELPER_SHAPE_ANALYSIS"),
            List.of(
                "DELETE",
                "CONSOLIDATE_BYTECODE",
                "REPLACE_WITH_SHARED_ADAPTER",
                "ALTER_BEAN_REGISTRATION_LOGIC"
            )
        );
    }

    private SyntheticSafetyValidationReport validation() {
        return new SyntheticSafetyValidationReport(
            PROTOTYPE_VERSION,
            Instant.now().toString(),
            false,
            true,
            List.of(
                "bytecode verification",
                "Spring context smoke",
                "bean definition count comparison",
                "endpoint behavior comparison",
                "runtime origin proof",
                "single-run memory screen before mutation",
                "3-pair confirmation before public claim"
            ),
            List.of(
                "Inventory/report-only implementation is active.",
                "No generated-class bytecode mutation is enabled.",
                "Unsafe proxy families are deferred."
            )
        );
    }

    private void writeSelectionMarkdown(File target, SyntheticPrototypeFamilySelection selection) throws IOException {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Synthetic Prototype Family Selection\n\n");
        markdown.append("- Selected family: `").append(selection.selectedFamily()).append("`\n");
        markdown.append("- Implementation mode: `").append(selection.implementationMode()).append("`\n");
        markdown.append("- Bytecode mutation enabled: `").append(selection.bytecodeMutationEnabled()).append("`\n\n");
        markdown.append("## Reasons\n\n");
        for (String reason : selection.reasons()) {
            markdown.append("- ").append(reason).append('\n');
        }
        markdown.append("\n## Deferred Families\n\n");
        for (GeneratedClassFamily family : selection.deferredFamilies()) {
            markdown.append("- `").append(family).append("`\n");
        }
        Files.writeString(target.toPath(), markdown.toString(), StandardCharsets.UTF_8);
    }

    private void writePrototypeMarkdown(
        File target,
        SyntheticOptimizerPrototypeReport prototype,
        SyntheticSafetyValidationReport validation
    ) throws IOException {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Synthetic Optimizer Prototype Report\n\n");
        markdown.append("- Family: `").append(prototype.family()).append("`\n");
        markdown.append("- Mode: `").append(prototype.mode()).append("`\n");
        markdown.append("- Bytecode mutation enabled: `").append(prototype.bytecodeMutationEnabled()).append("`\n");
        markdown.append("- Affected classes: `").append(prototype.affectedClassCount()).append("`\n");
        markdown.append("- Affected class bytes: `").append(prototype.affectedClassBytes()).append("`\n\n");
        markdown.append("## Proposed Report-Only Transforms\n\n");
        for (String transform : prototype.proposedTransforms()) {
            markdown.append("- `").append(transform).append("`\n");
        }
        markdown.append("\n## Blocked Transforms\n\n");
        for (String transform : prototype.blockedTransforms()) {
            markdown.append("- `").append(transform).append("`\n");
        }
        markdown.append("\n## Validation Gates\n\n");
        for (String gate : validation.requiredGates()) {
            markdown.append("- ").append(gate).append('\n');
        }
        markdown.append("\nNo generated-class bytecode mutation is performed by this prototype report.\n");
        Files.writeString(target.toPath(), markdown.toString(), StandardCharsets.UTF_8);
    }
}
