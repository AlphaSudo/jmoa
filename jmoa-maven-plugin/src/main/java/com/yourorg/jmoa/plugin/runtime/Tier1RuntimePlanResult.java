package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;

import java.util.List;

public record Tier1RuntimePlanResult(
    List<Tier1RuntimePlan> supportedPlans,
    List<LambdaFilterDecision> unsupportedTier1Sites
) {

    public Tier1RuntimePlanResult {
        supportedPlans = List.copyOf(supportedPlans);
        unsupportedTier1Sites = List.copyOf(unsupportedTier1Sites);
    }
}
