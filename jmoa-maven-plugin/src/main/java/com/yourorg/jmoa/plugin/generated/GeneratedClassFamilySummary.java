package com.yourorg.jmoa.plugin.generated;

import java.util.Map;

public record GeneratedClassFamilySummary(
    GeneratedClassFamily family,
    int classCount,
    int generatedLikeClassCount,
    long classFileBytes,
    int methodCount,
    int syntheticMethodCount,
    int bridgeMethodCount,
    int lambdaMethodCount,
    int invokedynamicCount,
    Map<String, Long> artifactCounts
) {

    public GeneratedClassFamilySummary {
        artifactCounts = artifactCounts == null ? Map.of() : Map.copyOf(artifactCounts);
    }
}
