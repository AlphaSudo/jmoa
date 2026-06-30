package com.yourorg.jmoa.plugin.framework;

import java.util.EnumMap;
import java.util.Map;

public final class FrameworkSafetySummary {

    private final Map<FrameworkSafetyLevel, Integer> byLevel = new EnumMap<>(FrameworkSafetyLevel.class);
    private final Map<FrameworkSafetyReason, Integer> byReason = new EnumMap<>(FrameworkSafetyReason.class);
    private int totalFrameworkSites;
    private int allowedFrameworkSites;
    private int deniedFrameworkSites;

    public void record(FrameworkSafetyDecision decision) {
        byLevel.merge(decision.level(), 1, Integer::sum);

        if (decision.level() != FrameworkSafetyLevel.APPLICATION) {
            totalFrameworkSites++;
            if (decision.allowed()) {
                allowedFrameworkSites++;
            } else {
                deniedFrameworkSites++;
            }
        }

        for (FrameworkSafetyReason reason : decision.reasons()) {
            byReason.merge(reason, 1, Integer::sum);
        }
    }

    public int totalFrameworkSites() {
        return totalFrameworkSites;
    }

    public int allowedFrameworkSites() {
        return allowedFrameworkSites;
    }

    public int deniedFrameworkSites() {
        return deniedFrameworkSites;
    }

    public Map<FrameworkSafetyLevel, Integer> byLevel() {
        return Map.copyOf(byLevel);
    }

    public Map<FrameworkSafetyReason, Integer> byReason() {
        return Map.copyOf(byReason);
    }
}
