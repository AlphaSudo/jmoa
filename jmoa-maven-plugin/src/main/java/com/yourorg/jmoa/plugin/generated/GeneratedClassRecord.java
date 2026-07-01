package com.yourorg.jmoa.plugin.generated;

import java.util.List;

public record GeneratedClassRecord(
    String className,
    String internalName,
    String sourceRoot,
    String sourcePath,
    String artifact,
    GeneratedClassFamily family,
    List<String> classFlags,
    int methodCount,
    int syntheticMethodCount,
    int bridgeMethodCount,
    int lambdaMethodCount,
    long classFileBytes,
    int constantPoolCount,
    int invokedynamicCount,
    List<String> proxyIndicators,
    GeneratedClassRiskLevel riskLevel,
    Boolean runtimeLoaded,
    boolean generatedLike
) {

    public GeneratedClassRecord {
        classFlags = classFlags == null ? List.of() : List.copyOf(classFlags);
        proxyIndicators = proxyIndicators == null ? List.of() : List.copyOf(proxyIndicators);
    }
}
