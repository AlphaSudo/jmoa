package com.yourorg.jmoa.plugin.reducer;

public enum ReducerSafetyCategory {
    SAFE_REPORT_ONLY,
    SAFE_OPT_IN_RELEASE,
    UNSAFE_VERIFICATION,
    UNSAFE_FRAMEWORK_SEMANTIC,
    UNSAFE_DIAGNOSTIC_CRITICAL,
    UNKNOWN
}
