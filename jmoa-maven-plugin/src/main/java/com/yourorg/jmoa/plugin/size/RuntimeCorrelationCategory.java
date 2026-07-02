package com.yourorg.jmoa.plugin.size;

public enum RuntimeCorrelationCategory {
    STATIC_ONLY_RISK,
    RUNTIME_LOADED_COLD,
    RUNTIME_LOADED_HOT,
    WORKLOAD_SURVIVOR,
    MEMORY_CORRELATED,
    STARTUP_CORRELATED,
    UNKNOWN_NEEDS_JFR
}

