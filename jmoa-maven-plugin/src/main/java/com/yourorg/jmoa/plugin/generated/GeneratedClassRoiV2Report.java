package com.yourorg.jmoa.plugin.generated;

import java.util.List;

public record GeneratedClassRoiV2Report(
    String metadataVersion,
    String generatedAt,
    String profile,
    List<GeneratedClassRoiV2FamilyFeature> families
) {

    public GeneratedClassRoiV2Report {
        families = families == null ? List.of() : List.copyOf(families);
    }
}
