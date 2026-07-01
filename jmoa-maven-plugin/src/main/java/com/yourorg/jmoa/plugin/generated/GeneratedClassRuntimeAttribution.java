package com.yourorg.jmoa.plugin.generated;

import java.util.List;

public record GeneratedClassRuntimeAttribution(
    String metadataVersion,
    String generatedAt,
    String classLoadLog,
    String classHistogram,
    int totalRuntimeLoadedClasses,
    int totalGeneratedRuntimeLoadedClasses,
    int totalRuntimeUnloadedClasses,
    long totalHistogramBytes,
    List<GeneratedClassFamilyRuntimeAttribution> families,
    List<GeneratedClassRuntimeClassRecord> classes
) {

    public GeneratedClassRuntimeAttribution {
        families = families == null ? List.of() : List.copyOf(families);
        classes = classes == null ? List.of() : List.copyOf(classes);
    }
}
