package com.yourorg.jmoa.plugin.weave;

import java.util.List;

public record LambdaWeaveSanityClassResult(
    int expectedEligibleSites,
    int rewrittenEligibleSites,
    int remainingEligibleSites,
    int unexpectedRemovedSites,
    List<String> remainingEligibleSiteKeys,
    List<String> unexpectedRemovedSiteKeys
) {
    public LambdaWeaveSanityClassResult {
        remainingEligibleSiteKeys = List.copyOf(remainingEligibleSiteKeys);
        unexpectedRemovedSiteKeys = List.copyOf(unexpectedRemovedSiteKeys);
    }
}
