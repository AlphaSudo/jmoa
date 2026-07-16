package com.yourorg.jmoa.plugin.reducer;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class DebugMetadataSavingsEstimator {

    private final ReducerConfig config;
    private final ClassDebugMetadataInspector inspector = new ClassDebugMetadataInspector();
    private final JarSafetyInspector safetyInspector = new JarSafetyInspector();
    private final ArtifactSelectionPolicy selectionPolicy;

    public DebugMetadataSavingsEstimator(ReducerConfig config) {
        this.config = config;
        this.selectionPolicy = new ArtifactSelectionPolicy(config);
    }

    public ReducerReport estimate() throws IOException {
        List<JarReductionRecord> jars = new ArrayList<>();
        for (File jar : jarFiles(config.inputDir())) {
            jars.add(estimateJar(jar));
        }
        return report(false, jars, List.of(), new ApplicationClassReducer(config).reduceOrEstimate());
    }

    ReducerReport report(boolean mutationEnabled, List<JarReductionRecord> jars) {
        return report(mutationEnabled, jars, List.of(), ApplicationReductionReport.disabled());
    }

    ReducerReport report(
        boolean mutationEnabled,
        List<JarReductionRecord> jars,
        List<RawReducerClassAuditRecord> rawClassAudits
    ) {
        return report(mutationEnabled, jars, rawClassAudits, ApplicationReductionReport.disabled());
    }

    ReducerReport report(
        boolean mutationEnabled,
        List<JarReductionRecord> jars,
        List<RawReducerClassAuditRecord> rawClassAudits,
        ApplicationReductionReport application
    ) {
        return new ReducerReport(
            "v2q-debug-metadata-reducer",
            Instant.now().toString(),
            mutationEnabled,
            config.reportOnly(),
            config.profile(),
            config.parsedEngine().propertyValue(),
            jars.size(),
            jars.stream().mapToInt(JarReductionRecord::classCount).sum() + application.classCount(),
            jars.stream().mapToLong(JarReductionRecord::originalBytes).sum(),
            jars.stream().mapToLong(JarReductionRecord::reducedBytes).sum(),
            jars.stream().mapToLong(JarReductionRecord::estimatedRemovableBytes).sum() + application.estimatedRemovableBytes(),
            jars.stream().mapToLong(JarReductionRecord::removedBytes).sum() + application.removedBytes(),
            new ReducerSafetyPolicy().taxonomy(),
            jars,
            rawClassAudits,
            application,
            List.of(
                "V2-E only allows LocalVariableTable and LocalVariableTypeTable stripping.",
                "LineNumberTable, StackMapTable, annotations, signatures, and BootstrapMethods are preserved.",
                "The default asm engine skips BootstrapMethods-bearing classes during mutation; the opt-in raw engine preserves BootstrapMethods while rewriting only Code attribute debug tables.",
                "V2-Q application-class reduction is raw-engine-only and admits only ORDINARY_APPLICATION classes; generated/proxy families remain report-only or blocked.",
                "Performance claims require V2-C confirmation and V2-D attribution."
            )
        );
    }

    private JarReductionRecord estimateJar(File jar) throws IOException {
        List<ClassReductionRecord> classes = new ArrayList<>();
        long estimated = 0;
        int classCount = 0;
        boolean selected = selectionPolicy.isSelected(jar.getName());
        JarSafetyAssessment safety;
        try (JarFile input = new JarFile(jar)) {
            safety = safetyInspector.assess(input);
            var entries = input.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                classCount++;
                byte[] bytes;
                try (var stream = input.getInputStream(entry)) {
                    bytes = stream.readAllBytes();
                }
                ClassDebugMetadata metadata = inspector.inspect(bytes);
                if (selected) {
                    estimated += metadata.removableBytes();
                }
                if (metadata.removableBytes() > 0) {
                    String status = selected
                        ? (safety.mutationAllowed() ? "ESTIMATED" : safety.skipReason())
                        : "SKIPPED_ARTIFACT_POLICY";
                    classes.add(recordFor(jar.getName(), entry.getName(), metadata, bytes.length, bytes.length, status));
                }
            }
        }
        classes.sort(Comparator.comparingLong((ClassReductionRecord r) ->
            r.localVariableTableBytesRemoved() + r.localVariableTypeTableBytesRemoved()).reversed());
        String selectionSkip = selected ? null : "SKIPPED_ARTIFACT_POLICY";
        String skipReason = selectionSkip != null ? selectionSkip : safety.skipReason();
        return new JarReductionRecord(
            jar.getName(),
            jar.getAbsolutePath(),
            null,
            JarSafetyInspector.sha256(jar),
            null,
            jar.length(),
            jar.length(),
            estimated,
            0,
            classCount,
            0,
            0,
            safety.signedJar(),
            safety.multiReleaseJar(),
            safety.sealedJar(),
            skipReason,
            "preserve",
            selectionSkip == null && safety.mutationAllowed() ? "ESTIMATED" : skipReason,
            classes.stream().limit(200).toList()
        );
    }

    static ClassReductionRecord recordFor(
        String artifact,
        String entryName,
        ClassDebugMetadata before,
        int originalBytes,
        int reducedBytes,
        String status
    ) {
        return new ClassReductionRecord(
            before.className(),
            artifact,
            entryName,
            originalBytes,
            reducedBytes,
            before.localVariableTableBytes(),
            before.localVariableTypeTableBytes(),
            true,
            true,
            true,
            true,
            true,
            true,
            status
        );
    }

    static List<File> jarFiles(File inputDir) throws IOException {
        if (inputDir == null || !inputDir.isDirectory()) {
            return List.of();
        }
        try (var stream = java.nio.file.Files.walk(inputDir.toPath())) {
            return stream
                .filter(java.nio.file.Files::isRegularFile)
                .map(java.nio.file.Path::toFile)
                .filter(file -> file.getName().endsWith(".jar"))
                .sorted(Comparator.comparing(File::getAbsolutePath))
                .toList();
        }
    }
}
