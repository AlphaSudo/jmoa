package com.yourorg.jmoa.plugin.size;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;

public record AttributeFootprintRecord(
    String className,
    String artifact,
    long totalAttributeBytes,
    long debugAttributeBytes,
    long annotationAttributeBytes,
    long verificationAttributeBytes,
    long metadataAttributeBytes,
    long stackMapTableBytes,
    long lineNumberTableBytes,
    long localVariableTableBytes,
    long sourceFileAttributeBytes,
    long bootstrapMethodsAttributeBytes,
    GeneratedClassFamily generatedFamily
) {
}
