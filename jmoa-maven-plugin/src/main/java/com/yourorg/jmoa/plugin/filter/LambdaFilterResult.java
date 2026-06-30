package com.yourorg.jmoa.plugin.filter;

import com.yourorg.jmoa.plugin.framework.FrameworkSafetyDecision;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetySummary;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record LambdaFilterResult(List<LambdaFilterDecision> decisions) {

    public LambdaFilterResult {
        decisions = List.copyOf(decisions);
    }

    public List<LambdaFilterDecision> eligible() {
        return decisions.stream().filter(LambdaFilterDecision::eligible).toList();
    }

    public List<LambdaFilterDecision> excluded() {
        return decisions.stream().filter(decision -> !decision.eligible()).toList();
    }

    public List<LambdaFilterDecision> tier1Eligible() {
        return decisions.stream()
            .filter(LambdaFilterDecision::eligible)
            .filter(decision -> decision.tier() == LambdaTier.TIER1)
            .toList();
    }

    public List<LambdaFilterDecision> tier2Eligible() {
        return decisions.stream()
            .filter(LambdaFilterDecision::eligible)
            .filter(decision -> decision.tier() == LambdaTier.TIER2)
            .toList();
    }

    public Map<ExclusionReason, Long> exclusionCounts() {
        Map<ExclusionReason, Long> counts = new EnumMap<>(ExclusionReason.class);
        for (ExclusionReason reason : ExclusionReason.values()) {
            counts.put(reason, decisions.stream().filter(decision -> reason == decision.exclusionReason()).count());
        }
        return counts;
    }

    public Map<ExclusionReason, Double> exclusionPercentages() {
        int total = decisions.size();
        Map<ExclusionReason, Double> percentages = new EnumMap<>(ExclusionReason.class);
        for (Map.Entry<ExclusionReason, Long> entry : exclusionCounts().entrySet()) {
            double percentage = total == 0 ? 0.0d : (entry.getValue() * 100.0d) / total;
            percentages.put(entry.getKey(), percentage);
        }
        return percentages;
    }

    public long observedSiteCount() {
        return decisions.stream().filter(LambdaFilterDecision::observedInProfile).count();
    }

    public long inferredColdSiteCount() {
        return decisions.stream().filter(LambdaFilterDecision::inferredCold).count();
    }

    public FrameworkSafetySummary frameworkSafetySummary() {
        FrameworkSafetySummary summary = new FrameworkSafetySummary();
        for (LambdaFilterDecision decision : decisions) {
            FrameworkSafetyDecision frameworkDecision = decision.frameworkSafetyDecision();
            if (frameworkDecision != null) {
                summary.record(frameworkDecision);
            }
        }
        return summary;
    }
}
