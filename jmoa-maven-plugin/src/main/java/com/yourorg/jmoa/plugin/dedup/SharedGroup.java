package com.yourorg.jmoa.plugin.dedup;

import com.yourorg.jmoa.plugin.scanner.LambdaSite;

import java.util.List;

public record SharedGroup(
    DeduplicationKey key,
    String synthClassName,
    List<LambdaSite> sites,
    AccessPlan accessPlan
) {

    public boolean needsAccessWidening() {
        return accessPlan.requiresAccessWidening();
    }

    public String targetPackageInternal() {
        return accessPlan.targetPackageInternal();
    }

    public AccessTier accessTier() {
        return accessPlan.tier();
    }
}
