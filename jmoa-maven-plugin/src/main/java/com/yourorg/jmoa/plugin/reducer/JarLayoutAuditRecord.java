package com.yourorg.jmoa.plugin.reducer;

import java.util.List;

public record JarLayoutAuditRecord(
    int inputEntryCount,
    int outputEntryCount,
    boolean entryOrderPreserved,
    int intentionallyChangedEntryCount,
    int unchangedEntryCount,
    int unchangedRawPayloadPreservedCount,
    int unchangedMetadataPreservedCount,
    boolean allUnchangedRawPayloadsPreserved,
    boolean allUnchangedMetadataPreserved,
    List<String> failures
) {
    public JarLayoutAuditRecord {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
