package com.yourorg.jmoa.plugin.reducer;

public record ReducerSafetyEntry(
    String attribute,
    ReducerSafetyCategory category,
    String reason
) {
}
