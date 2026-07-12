package com.yourorg.jmoa.plugin.reducer;

import java.util.List;

public record ApplicationReductionReport(
    boolean requested,
    String inputDirectory,
    String outputDirectory,
    int classCount,
    int reducedClassCount,
    long estimatedRemovableBytes,
    long removedBytes,
    int auditedClassCount,
    int failedAuditCount,
    List<ApplicationClassReductionRecord> classes,
    List<RawReducerClassAuditRecord> rawClassAudits,
    List<GeneratedFamilyAssessment> familyTaxonomy
) {
    public ApplicationReductionReport {
        classes = classes == null ? List.of() : List.copyOf(classes);
        rawClassAudits = rawClassAudits == null ? List.of() : List.copyOf(rawClassAudits);
        familyTaxonomy = familyTaxonomy == null ? List.of() : List.copyOf(familyTaxonomy);
    }

    public static ApplicationReductionReport disabled() {
        return new ApplicationReductionReport(false, "", "", 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
    }
}
