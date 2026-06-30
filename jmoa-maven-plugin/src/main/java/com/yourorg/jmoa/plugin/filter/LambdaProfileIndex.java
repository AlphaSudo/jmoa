package com.yourorg.jmoa.plugin.filter;

import java.util.Map;
import java.util.Set;

public record LambdaProfileIndex(
    Map<String, Long> invocationCountsBySiteKey,
    Set<String> hotClasses,
    Set<String> coldClasses
) {

    public LambdaProfileIndex {
        invocationCountsBySiteKey = invocationCountsBySiteKey == null ? Map.of() : Map.copyOf(invocationCountsBySiteKey);
        hotClasses = hotClasses == null ? Set.of() : Set.copyOf(hotClasses);
        coldClasses = coldClasses == null ? Set.of() : Set.copyOf(coldClasses);
    }

    public static LambdaProfileIndex empty() {
        return new LambdaProfileIndex(Map.of(), Set.of(), Set.of());
    }

    public boolean hasSite(String siteKey) {
        return invocationCountsBySiteKey.containsKey(siteKey);
    }

    public long invocationCount(String siteKey) {
        return invocationCountsBySiteKey.getOrDefault(siteKey, 0L);
    }

    public boolean isColdClass(String ownerInternalName) {
        return coldClasses.contains(ownerInternalName);
    }
}
