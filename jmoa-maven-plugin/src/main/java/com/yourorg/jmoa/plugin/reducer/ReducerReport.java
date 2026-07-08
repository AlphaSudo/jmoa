package com.yourorg.jmoa.plugin.reducer;

import java.util.List;

public record ReducerReport(
    String metadataVersion,
    String generatedAt,
    boolean mutationEnabled,
    boolean reportOnly,
    String profile,
    String engine,
    int jarCount,
    int classCount,
    long totalOriginalBytes,
    long totalReducedBytes,
    long totalEstimatedRemovableBytes,
    long totalRemovedBytes,
    ReducerSafetyTaxonomy safetyTaxonomy,
    List<JarReductionRecord> artifacts,
    List<RawReducerClassAuditRecord> rawClassAudits,
    List<String> boundaries
) {
    public ReducerReport {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        rawClassAudits = rawClassAudits == null ? List.of() : List.copyOf(rawClassAudits);
        boundaries = boundaries == null ? List.of() : List.copyOf(boundaries);
    }
}
