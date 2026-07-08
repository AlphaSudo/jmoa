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
