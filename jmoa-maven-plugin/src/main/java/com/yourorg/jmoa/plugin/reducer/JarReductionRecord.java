package com.yourorg.jmoa.plugin.reducer;

import java.util.List;

public record JarReductionRecord(
    String artifact,
    String sourceJar,
    String reducedJar,
    String inputSha256,
    String outputSha256,
    long originalBytes,
    long reducedBytes,
    long estimatedRemovableBytes,
    long removedBytes,
    int classCount,
    int reducedClassCount,
    int skippedBootstrapMethodsClassCount,
    boolean signedJar,
    boolean multiReleaseJar,
    boolean sealedJar,
    String skipReason,
    String timestampPolicy,
    String status,
    List<ClassReductionRecord> classes
) {
    public JarReductionRecord(
        String artifact,
        String sourceJar,
        String reducedJar,
        long originalBytes,
        long reducedBytes,
        long estimatedRemovableBytes,
        long removedBytes,
        int classCount,
        int reducedClassCount,
        String status,
        List<ClassReductionRecord> classes
    ) {
        this(
            artifact,
            sourceJar,
            reducedJar,
            null,
            null,
            originalBytes,
            reducedBytes,
            estimatedRemovableBytes,
            removedBytes,
            classCount,
            reducedClassCount,
            0,
            false,
            false,
            false,
            null,
            "preserve",
            status,
            classes
        );
    }

    public JarReductionRecord {
        classes = classes == null ? List.of() : List.copyOf(classes);
    }
}
