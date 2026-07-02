package com.yourorg.jmoa.plugin.size;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;

public record BytecodeRuntimeClassCorrelation(
    String className,
    String artifact,
    GeneratedClassFamily generatedFamily,
    boolean generatedLike,
    long classfileBytes,
    int largestMethodCodeLength,
    long totalMethodCodeBytes,
    int constantPoolCount,
    long totalAttributeBytes,
    boolean runtimeLoaded,
    boolean runtimeUnloaded,
    String loadOrigin,
    long histogramInstances,
    long histogramBytes,
    RuntimeCorrelationCategory category,
    String priority
) {
}

