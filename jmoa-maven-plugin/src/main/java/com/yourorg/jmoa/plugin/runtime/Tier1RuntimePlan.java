package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;

public record Tier1RuntimePlan(
    int slotId,
    RuntimeAdapterKind adapterKind,
    LambdaFilterDecision decision
) {
}
