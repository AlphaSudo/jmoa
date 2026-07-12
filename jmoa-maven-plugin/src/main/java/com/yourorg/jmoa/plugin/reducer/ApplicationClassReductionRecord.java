package com.yourorg.jmoa.plugin.reducer;

public record ApplicationClassReductionRecord(
    String className,
    String relativePath,
    String family,
    GeneratedFamilyAdmission admission,
    int originalBytes,
    int reducedBytes,
    long localVariableTableBytesRemoved,
    long localVariableTypeTableBytesRemoved,
    String status,
    String reason
) {
}
