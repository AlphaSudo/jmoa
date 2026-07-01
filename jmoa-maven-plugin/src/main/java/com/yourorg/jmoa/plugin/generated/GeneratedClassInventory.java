package com.yourorg.jmoa.plugin.generated;

import java.util.List;

public record GeneratedClassInventory(
    String metadataVersion,
    String generatedAt,
    int totalClassesScanned,
    int generatedLikeClasses,
    long totalClassFileBytes,
    List<GeneratedClassFamilySummary> familyBreakdown,
    List<GeneratedClassRecord> classes
) {

    public GeneratedClassInventory {
        familyBreakdown = familyBreakdown == null ? List.of() : List.copyOf(familyBreakdown);
        classes = classes == null ? List.of() : List.copyOf(classes);
    }
}
