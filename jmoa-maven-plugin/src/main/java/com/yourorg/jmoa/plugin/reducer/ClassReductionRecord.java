package com.yourorg.jmoa.plugin.reducer;

public record ClassReductionRecord(
    String className,
    String artifact,
    String entryName,
    int originalBytes,
    int reducedBytes,
    long localVariableTableBytesRemoved,
    long localVariableTypeTableBytesRemoved,
    boolean lineNumberTablePreserved,
    boolean sourceFilePreserved,
    boolean stackMapTablePreserved,
    boolean annotationsPreserved,
    boolean signaturePreserved,
    boolean bootstrapMethodsPreserved,
    String status
) {
}
