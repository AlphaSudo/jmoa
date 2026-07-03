package com.yourorg.jmoa.plugin.reducer;

public record ClassDebugMetadata(
    String className,
    int originalBytes,
    long localVariableTableBytes,
    long localVariableTypeTableBytes,
    long lineNumberTableBytes,
    long stackMapTableBytes,
    long annotationAttributeBytes,
    long signatureAttributeBytes,
    long bootstrapMethodsAttributeBytes,
    long sourceFileAttributeBytes
) {

    public long removableBytes() {
        return localVariableTableBytes + localVariableTypeTableBytes;
    }
}
