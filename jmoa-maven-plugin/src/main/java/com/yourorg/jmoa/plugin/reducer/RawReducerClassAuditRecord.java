package com.yourorg.jmoa.plugin.reducer;

import java.util.List;

public record RawReducerClassAuditRecord(
    String className,
    String artifact,
    String entryName,
    String engine,
    String originalClassSha256,
    String reducedClassSha256,
    int originalBytes,
    int reducedBytes,
    List<String> removedAttributes,
    String normalizedStructureSha256,
    String originalConstantPoolSha256,
    String reducedConstantPoolSha256,
    String originalBytecodeSha256,
    String reducedBytecodeSha256,
    String originalStackMapTableSha256,
    String reducedStackMapTableSha256,
    String originalBootstrapMethodsSha256,
    String reducedBootstrapMethodsSha256,
    String originalAnnotationsSha256,
    String reducedAnnotationsSha256,
    boolean componentDigestsPreserved,
    boolean preservedNonTargetStructures,
    boolean lineNumberTablePreserved,
    boolean sourceFilePreserved,
    boolean stackMapTablePreserved,
    boolean annotationsPreserved,
    boolean signaturePreserved,
    boolean bootstrapMethodsPreserved,
    String status
) {
    public RawReducerClassAuditRecord {
        removedAttributes = removedAttributes == null ? List.of() : List.copyOf(removedAttributes);
    }
}
