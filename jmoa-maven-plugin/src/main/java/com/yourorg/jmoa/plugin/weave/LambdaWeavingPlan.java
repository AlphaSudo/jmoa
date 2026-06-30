package com.yourorg.jmoa.plugin.weave;

import java.util.Map;

public record LambdaWeavingPlan(Map<String, LambdaWeaveTarget> targetsBySiteKey) {

    public LambdaWeavingPlan {
        targetsBySiteKey = Map.copyOf(targetsBySiteKey);
    }

    public LambdaWeaveTarget targetFor(String siteKey) {
        return targetsBySiteKey.get(siteKey);
    }

    public boolean isEmpty() {
        return targetsBySiteKey.isEmpty();
    }
}
