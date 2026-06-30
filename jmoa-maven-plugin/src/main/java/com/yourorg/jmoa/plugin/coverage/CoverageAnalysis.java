package com.yourorg.jmoa.plugin.coverage;

import java.util.List;

public record CoverageAnalysis(
    String executionMode,
    List<String> classRootsScanned,
    int classesScanned,
    int totalLambdaSites,
    int statelessCandidateSites,
    int profileSiteCount,
    int observedCurrentSites,
    int newSiteCount,
    int missingProfileSiteCount,
    List<String> newSiteKeys,
    List<String> missingProfileSiteKeys
) {
    public CoverageAnalysis {
        classRootsScanned = List.copyOf(classRootsScanned);
        newSiteKeys = List.copyOf(newSiteKeys);
        missingProfileSiteKeys = List.copyOf(missingProfileSiteKeys);
    }
}
