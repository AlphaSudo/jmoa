package com.yourorg.jmoa.plugin.report;

import java.util.Set;

public record ObservedAdmissionReportContext(
    boolean enabled,
    String requestedFile,
    String resolvedFile,
    boolean loaded,
    Set<String> siteKeys,
    String tierMode
) {

    public ObservedAdmissionReportContext {
        requestedFile = requestedFile == null ? "" : requestedFile;
        resolvedFile = resolvedFile == null ? "" : resolvedFile;
        siteKeys = siteKeys == null ? Set.of() : Set.copyOf(siteKeys);
        tierMode = tierMode == null ? "" : tierMode;
    }

    public static ObservedAdmissionReportContext disabled() {
        return new ObservedAdmissionReportContext(false, "", "", false, Set.of(), "");
    }
}
