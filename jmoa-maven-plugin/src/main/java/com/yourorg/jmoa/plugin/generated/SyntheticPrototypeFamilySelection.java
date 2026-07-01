package com.yourorg.jmoa.plugin.generated;

import java.util.List;

public record SyntheticPrototypeFamilySelection(
    String metadataVersion,
    String generatedAt,
    GeneratedClassFamily selectedFamily,
    String implementationMode,
    boolean bytecodeMutationEnabled,
    List<String> reasons,
    List<GeneratedClassFamily> deferredFamilies,
    List<String> acceptanceGates
) {

    public SyntheticPrototypeFamilySelection {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        deferredFamilies = deferredFamilies == null ? List.of() : List.copyOf(deferredFamilies);
        acceptanceGates = acceptanceGates == null ? List.of() : List.copyOf(acceptanceGates);
    }
}
