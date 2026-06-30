package com.yourorg.jmoa.plugin.coverage;

import com.yourorg.jmoa.plugin.filter.LambdaProfileIndex;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import com.yourorg.jmoa.plugin.scanner.ScanResult;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class CoverageAnalyzer {

    public CoverageAnalysis analyze(
        ScanResult scanResult,
        LambdaProfileIndex profileIndex,
        String executionMode,
        List<String> classRootsScanned
    ) {
        Set<String> currentSiteKeys = new TreeSet<>(
            scanResult.metadata().stream()
                .map(LambdaMeta::siteKey)
                .toList()
        );
        Set<String> profileSiteKeys = new TreeSet<>(profileIndex.invocationCountsBySiteKey().keySet());

        List<String> newSiteKeys = currentSiteKeys.stream()
            .filter(siteKey -> !profileSiteKeys.contains(siteKey))
            .toList();
        List<String> missingProfileSiteKeys = profileSiteKeys.stream()
            .filter(siteKey -> !currentSiteKeys.contains(siteKey))
            .toList();

        int observedCurrentSites = (int) currentSiteKeys.stream()
            .filter(profileSiteKeys::contains)
            .count();

        return new CoverageAnalysis(
            executionMode,
            classRootsScanned,
            currentSiteKeys.size(),
            scanResult.totalLambdaSites(),
            scanResult.metadata().size(),
            profileSiteKeys.size(),
            observedCurrentSites,
            newSiteKeys.size(),
            missingProfileSiteKeys.size(),
            newSiteKeys,
            missingProfileSiteKeys
        );
    }
}
