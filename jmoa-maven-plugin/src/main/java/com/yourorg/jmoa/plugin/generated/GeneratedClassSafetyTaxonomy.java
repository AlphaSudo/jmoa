package com.yourorg.jmoa.plugin.generated;

import java.util.List;
import java.util.Map;

public record GeneratedClassSafetyTaxonomy(
    String metadataVersion,
    String generatedAt,
    int totalClasses,
    Map<GeneratedClassSafetyCategory, Integer> categoryCounts,
    List<GeneratedClassTransformEligibility> eligibility
) {

    public GeneratedClassSafetyTaxonomy {
        categoryCounts = categoryCounts == null ? Map.of() : Map.copyOf(categoryCounts);
        eligibility = eligibility == null ? List.of() : List.copyOf(eligibility);
    }
}
