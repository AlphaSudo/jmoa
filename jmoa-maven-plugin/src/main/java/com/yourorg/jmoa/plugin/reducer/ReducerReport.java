package com.yourorg.jmoa.plugin.reducer;

import java.util.List;

public record ReducerReport(
    String metadataVersion,
    String generatedAt,
    boolean mutationEnabled,
    boolean reportOnly,
    String profile,
    String engine,
    String operation,
    String jarLayout,
    int jarCount,
    int classCount,
    long totalOriginalBytes,
    long totalReducedBytes,
    long totalOutputDeltaBytes,
    long totalNetBytesSaved,
    long totalEstimatedRemovableBytes,
    long totalRemovedBytes,
    ReducerSafetyTaxonomy safetyTaxonomy,
    List<JarReductionRecord> artifacts,
    List<RawReducerClassAuditRecord> rawClassAudits,
    ApplicationReductionReport application,
    List<String> boundaries
) {
    public ReducerReport {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        rawClassAudits = rawClassAudits == null ? List.of() : List.copyOf(rawClassAudits);
        application = application == null ? ApplicationReductionReport.disabled() : application;
        boundaries = boundaries == null ? List.of() : List.copyOf(boundaries);
    }
}
