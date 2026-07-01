package com.yourorg.jmoa.plugin.size;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;

public record ClassfileSizeFamilySummary(
    GeneratedClassFamily generatedFamily,
    int classCount,
    int generatedLikeClassCount,
    long classfileBytes,
    long totalMethodCodeBytes,
    int largestMethodCodeLength,
    long totalAttributeBytes,
    long debugAttributeBytes,
    long annotationAttributeBytes,
    long constantPoolEntries
) {
}
