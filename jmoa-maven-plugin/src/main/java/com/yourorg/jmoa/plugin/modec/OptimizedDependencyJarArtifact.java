package com.yourorg.jmoa.plugin.modec;

import com.yourorg.jmoa.plugin.deps.DependencyCoordinate;

import java.io.File;

public record OptimizedDependencyJarArtifact(
    DependencyCoordinate coordinate,
    File originalJar,
    File optimizedJar,
    int rewrittenClasses,
    int unchangedClasses,
    int generatedAdapters,
    int copiedResources,
    int removedSignatures,
    long originalJarBytes,
    long optimizedJarBytes
) {

    public OptimizedDependencyJarArtifact(
        DependencyCoordinate coordinate,
        File originalJar,
        File optimizedJar,
        int rewrittenClasses,
        int unchangedClasses,
        int generatedAdapters,
        int copiedResources,
        int removedSignatures
    ) {
        this(
            coordinate,
            originalJar,
            optimizedJar,
            rewrittenClasses,
            unchangedClasses,
            generatedAdapters,
            copiedResources,
            removedSignatures,
            originalJar == null ? 0L : originalJar.length(),
            optimizedJar == null ? 0L : optimizedJar.length()
        );
    }

    public long jarByteDelta() {
        return optimizedJarBytes - originalJarBytes;
    }

    public boolean hasOptimizationChanges() {
        return rewrittenClasses > 0 || generatedAdapters > 0;
    }
}
