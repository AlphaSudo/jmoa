package com.yourorg.jmoa.plugin.reducer;

/**
 * Typed reducer/layout capability verdicts. The report never exposes one
 * generic "optimized" verdict: archive-byte savings, reproducible layout, and
 * resident-memory support are independent capabilities with separate evidence.
 */
public enum ReducerCapabilityVerdict {
    ARTIFACT_SIZE_WIN,
    REPRODUCIBLE_LAYOUT_WIN,
    RAM_NOT_SUPPORTED
}
