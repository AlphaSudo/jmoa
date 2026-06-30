package com.yourorg.jmoa.plugin.dedup;

public record AccessPlan(
    AccessTier tier,
    AccessResolver.Visibility sourceVisibility,
    boolean requiresAccessWidening,
    String targetPackageInternal,
    String rationale
) {

    public boolean isTier1() {
        return tier == AccessTier.TIER1_PUBLIC_LOOKUP;
    }

    public boolean isTier2() {
        return tier == AccessTier.TIER2_PACKAGE_DIRECT;
    }
}
