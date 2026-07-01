package com.yourorg.jmoa.plugin.generated;

import java.util.List;

public record GeneratedClassFamilyRuntimeAttribution(
    GeneratedClassFamily family,
    int staticClassCount,
    long staticClassFileBytes,
    int runtimeLoadedCount,
    int runtimeUnloadedCount,
    int staticAndLoadedCount,
    int runtimeOnlyLoadedCount,
    int histogramClassCount,
    long histogramInstanceCount,
    long histogramBytes,
    String survival,
    String optimizationPriority,
    List<GeneratedClassRuntimeClassRecord> topClasses
) {

    public GeneratedClassFamilyRuntimeAttribution {
        topClasses = topClasses == null ? List.of() : List.copyOf(topClasses);
    }
}
