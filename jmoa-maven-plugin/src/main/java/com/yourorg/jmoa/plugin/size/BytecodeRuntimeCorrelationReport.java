package com.yourorg.jmoa.plugin.size;

import java.util.List;
import java.util.Map;

public record BytecodeRuntimeCorrelationReport(
    String metadataVersion,
    String generatedAt,
    String classLoadLog,
    String classHistogram,
    int totalProfileClasses,
    int totalRuntimeLoadedClasses,
    int profileClassesObservedLoaded,
    int profileClassesWithHistogramInstances,
    long totalHistogramBytes,
    Map<RuntimeCorrelationCategory, Integer> categoryCounts,
    List<BytecodeRuntimeFamilyCorrelation> families,
    List<BytecodeRuntimeClassCorrelation> classes,
    List<BytecodeRuntimeMethodCorrelation> methods
) {
    public BytecodeRuntimeCorrelationReport {
        categoryCounts = categoryCounts == null ? Map.of() : Map.copyOf(categoryCounts);
        families = families == null ? List.of() : List.copyOf(families);
        classes = classes == null ? List.of() : List.copyOf(classes);
        methods = methods == null ? List.of() : List.copyOf(methods);
    }
}

