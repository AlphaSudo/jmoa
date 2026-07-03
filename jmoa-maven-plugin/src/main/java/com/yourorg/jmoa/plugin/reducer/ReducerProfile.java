package com.yourorg.jmoa.plugin.reducer;

import java.util.Locale;

public enum ReducerProfile {
    NONE,
    RELEASE_LOW_FOOTPRINT;

    public static ReducerProfile parse(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return ReducerProfile.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
