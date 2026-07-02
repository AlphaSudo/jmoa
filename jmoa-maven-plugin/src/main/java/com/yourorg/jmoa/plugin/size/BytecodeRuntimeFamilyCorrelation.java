package com.yourorg.jmoa.plugin.size;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;

public record BytecodeRuntimeFamilyCorrelation(
    GeneratedClassFamily generatedFamily,
    int staticClassCount,
    int runtimeLoadedClassCount,
    int workloadSurvivorClassCount,
    long classfileBytes,
    long loadedClassfileBytes,
    long histogramBytes,
    long histogramInstances
) {
}

