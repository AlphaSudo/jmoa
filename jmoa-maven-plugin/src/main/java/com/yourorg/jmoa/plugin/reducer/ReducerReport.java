package com.yourorg.jmoa.plugin.reducer;

import java.util.List;

public record ReducerReport(
    String metadataVersion,
    String generatedAt,
    boolean mutationEnabled,
    boolean reportOnly,
    String profile,
    int jarCount,
    int classCount,
    long totalOriginalBytes,
    long totalReducedBytes,
    long totalEstimatedRemovableBytes,
    long totalRemovedBytes,
    ReducerSafetyTaxonomy safetyTaxonomy,
    List<JarReductionRecord> artifacts,
    List<String> boundaries
) {
    public ReducerReport {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        boundaries = boundaries == null ? List.of() : List.copyOf(boundaries);
    }
}
