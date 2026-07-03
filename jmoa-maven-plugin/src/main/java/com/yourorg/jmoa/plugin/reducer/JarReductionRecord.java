package com.yourorg.jmoa.plugin.reducer;

import java.util.List;

public record JarReductionRecord(
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
    public JarReductionRecord {
        classes = classes == null ? List.of() : List.copyOf(classes);
    }
}
