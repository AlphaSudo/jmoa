package com.yourorg.jmoa.plugin.modec;

import java.io.File;
import java.util.List;

public record OptimizedDependencyJarPackagingResult(
    File outputDirectory,
    List<OptimizedDependencyJarArtifact> artifacts,
    HybridPackagingSummary hybridPackagingSummary
) {

    public OptimizedDependencyJarPackagingResult {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        hybridPackagingSummary = hybridPackagingSummary == null ? HybridPackagingSummary.disabled() : hybridPackagingSummary;
    }

    public OptimizedDependencyJarPackagingResult(
        File outputDirectory,
        List<OptimizedDependencyJarArtifact> artifacts
    ) {
        this(outputDirectory, artifacts, HybridPackagingSummary.disabled());
    }

    public static OptimizedDependencyJarPackagingResult disabled(File outputDirectory) {
        return new OptimizedDependencyJarPackagingResult(outputDirectory, List.of());
    }

    public OptimizedDependencyJarPackagingResult withHybridPackagingSummary(HybridPackagingSummary summary) {
        return new OptimizedDependencyJarPackagingResult(outputDirectory, artifacts, summary);
    }
}
