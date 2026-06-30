package com.yourorg.jmoa.plugin.weave;

import java.util.List;

public record LambdaWeaveSanitySummary(
    int expectedEligibleSites,
    int verifiedEligibleSites,
    int rewrittenEligibleSites,
    int remainingEligibleSites,
    int unexpectedRemovedSites,
    List<String> remainingEligibleSiteKeys,
    List<String> unexpectedRemovedSiteKeys
) {
    public LambdaWeaveSanitySummary {
        remainingEligibleSiteKeys = List.copyOf(remainingEligibleSiteKeys);
        unexpectedRemovedSiteKeys = List.copyOf(unexpectedRemovedSiteKeys);
    }

    public int unverifiedEligibleSites() {
        return Math.max(0, expectedEligibleSites - verifiedEligibleSites);
    }

    public boolean assertionsPassed() {
        return remainingEligibleSites == 0
            && unexpectedRemovedSites == 0
            && rewrittenEligibleSites == verifiedEligibleSites;
    }
}
