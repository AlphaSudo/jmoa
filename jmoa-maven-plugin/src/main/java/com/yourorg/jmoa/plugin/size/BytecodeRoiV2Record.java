package com.yourorg.jmoa.plugin.size;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRiskLevel;

public record BytecodeRoiV2Record(
    String className,
    String artifact,
    GeneratedClassFamily generatedFamily,
    GeneratedClassRiskLevel generatedRiskLevel,
    boolean generatedLike,
    long classfileBytes,
    int largestMethodCodeLength,
    long totalMethodCodeBytes,
    int constantPoolCount,
    long totalAttributeBytes,
    long debugAttributeBytes,
    MethodSizeRisk sizeRisk,
    String candidatePriority
) {
}
