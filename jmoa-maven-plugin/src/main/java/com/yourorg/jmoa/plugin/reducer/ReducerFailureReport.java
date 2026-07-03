package com.yourorg.jmoa.plugin.reducer;

public record ReducerFailureReport(
    String metadataVersion,
    String generatedAt,
    String errorType,
    String message,
    String action
) {
}
