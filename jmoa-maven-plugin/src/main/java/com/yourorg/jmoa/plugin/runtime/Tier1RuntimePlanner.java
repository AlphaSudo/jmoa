package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Tier1RuntimePlanner {

    public Tier1RuntimePlanResult plan(LambdaFilterResult filterResult) {
        List<LambdaFilterDecision> tier1Sites = filterResult.tier1Eligible().stream()
            .sorted(Comparator.comparing(decision -> decision.meta().siteKey()))
            .toList();

        List<Tier1RuntimePlan> supportedPlans = new ArrayList<>();
        List<LambdaFilterDecision> unsupportedTier1Sites = new ArrayList<>();
        Map<RuntimeAdapterKind, Integer> nextSlotIds = new EnumMap<>(RuntimeAdapterKind.class);

        for (LambdaFilterDecision decision : tier1Sites) {
            var adapterKind = RuntimeAdapterKind.fromSamInterface(decision.meta().samInterfaceInternalName());
            if (adapterKind.isPresent()) {
                RuntimeAdapterKind kind = adapterKind.get();
                int slotId = nextSlotIds.getOrDefault(kind, 0);
                supportedPlans.add(new Tier1RuntimePlan(slotId, kind, decision));
                nextSlotIds.put(kind, slotId + 1);
            } else {
                unsupportedTier1Sites.add(decision);
            }
        }

        return new Tier1RuntimePlanResult(supportedPlans, unsupportedTier1Sites);
    }
}
