package com.yourorg.jmoa.plugin.generated;

import java.util.List;

public record SyntheticSafetyValidationReport(
    String metadataVersion,
    String generatedAt,
    boolean bytecodeMutationAllowed,
    boolean semanticSmokeRequired,
    List<String> requiredGates,
    List<String> currentStatus
) {

    public SyntheticSafetyValidationReport {
        requiredGates = requiredGates == null ? List.of() : List.copyOf(requiredGates);
        currentStatus = currentStatus == null ? List.of() : List.copyOf(currentStatus);
    }
}
