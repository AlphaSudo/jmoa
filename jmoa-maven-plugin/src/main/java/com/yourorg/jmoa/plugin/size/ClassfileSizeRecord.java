package com.yourorg.jmoa.plugin.size;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRiskLevel;

public record ClassfileSizeRecord(
    String className,
    String internalName,
    String sourceRoot,
    String sourcePath,
    String artifact,
    String rootKind,
    long classfileBytes,
    int constantPoolCount,
    int fieldCount,
    int methodCount,
    int attributeCount,
    int interfacesCount,
    int innerClassCount,
    int nestMemberCount,
    int recordComponentCount,
    int bootstrapMethodsCount,
    int bootstrapMethodArgCount,
    int invokedynamicCount,
    int syntheticMethodCount,
    int bridgeMethodCount,
    int largestMethodCodeLength,
    long totalMethodCodeBytes,
    long totalAttributeBytes,
    long debugAttributeBytes,
    long annotationAttributeBytes,
    long stackMapTableBytes,
    long lineNumberTableBytes,
    long localVariableTableBytes,
    long sourceFileAttributeBytes,
    GeneratedClassFamily generatedFamily,
    GeneratedClassRiskLevel generatedRiskLevel,
    boolean generatedLike
) {
}
