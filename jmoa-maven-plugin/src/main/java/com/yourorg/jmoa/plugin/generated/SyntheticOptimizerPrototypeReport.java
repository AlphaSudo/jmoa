package com.yourorg.jmoa.plugin.generated;

import java.util.List;

public record SyntheticOptimizerPrototypeReport(
    String metadataVersion,
    String generatedAt,
    GeneratedClassFamily family,
    String mode,
    boolean bytecodeMutationEnabled,
    int affectedClassCount,
    long affectedClassBytes,
    List<GeneratedClassRecord> affectedClasses,
    List<String> proposedTransforms,
    List<String> blockedTransforms
) {

    public SyntheticOptimizerPrototypeReport {
        affectedClasses = affectedClasses == null ? List.of() : List.copyOf(affectedClasses);
        proposedTransforms = proposedTransforms == null ? List.of() : List.copyOf(proposedTransforms);
        blockedTransforms = blockedTransforms == null ? List.of() : List.copyOf(blockedTransforms);
    }
}
