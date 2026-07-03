package com.yourorg.jmoa.plugin.reducer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public final class DebugMetadataSavingsEstimator {

    private final ReducerConfig config;
    private final ClassDebugMetadataInspector inspector = new ClassDebugMetadataInspector();

    public DebugMetadataSavingsEstimator(ReducerConfig config) {
        this.config = config;
    }

    public ReducerReport estimate() throws IOException {
        List<JarReductionRecord> jars = new ArrayList<>();
        for (File jar : jarFiles(config.inputDir())) {
            jars.add(estimateJar(jar));
        }
        return report(false, jars);
    }

    ReducerReport report(boolean mutationEnabled, List<JarReductionRecord> jars) {
        return new ReducerReport(
            "v2-e-debug-metadata-reducer",
            Instant.now().toString(),
            mutationEnabled,
            config.reportOnly(),
            config.profile(),
            jars.size(),
            jars.stream().mapToInt(JarReductionRecord::classCount).sum(),
            jars.stream().mapToLong(JarReductionRecord::originalBytes).sum(),
            jars.stream().mapToLong(JarReductionRecord::reducedBytes).sum(),
            jars.stream().mapToLong(JarReductionRecord::estimatedRemovableBytes).sum(),
            jars.stream().mapToLong(JarReductionRecord::removedBytes).sum(),
            new ReducerSafetyPolicy().taxonomy(),
            jars,
            List.of(
                "V2-E only allows LocalVariableTable and LocalVariableTypeTable stripping.",
                "LineNumberTable, StackMapTable, annotations, signatures, and BootstrapMethods are preserved.",
                "Performance claims require V2-C confirmation and V2-D attribution."
            )
        );
    }

    private JarReductionRecord estimateJar(File jar) throws IOException {
        List<ClassReductionRecord> classes = new ArrayList<>();
        long estimated = 0;
        int classCount = 0;
        try (JarInputStream input = new JarInputStream(new FileInputStream(jar))) {
            JarEntry entry;
            while ((entry = input.getNextJarEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                classCount++;
                byte[] bytes = input.readAllBytes();
                ClassDebugMetadata metadata = inspector.inspect(bytes);
                estimated += metadata.removableBytes();
                if (metadata.removableBytes() > 0) {
                    classes.add(recordFor(jar.getName(), entry.getName(), metadata, bytes.length, bytes.length, "ESTIMATED"));
                }
            }
        }
        classes.sort(Comparator.comparingLong((ClassReductionRecord r) ->
            r.localVariableTableBytesRemoved() + r.localVariableTypeTableBytesRemoved()).reversed());
        return new JarReductionRecord(
            jar.getName(),
            jar.getAbsolutePath(),
            null,
            jar.length(),
            jar.length(),
            estimated,
            0,
            classCount,
            0,
            "ESTIMATED",
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
