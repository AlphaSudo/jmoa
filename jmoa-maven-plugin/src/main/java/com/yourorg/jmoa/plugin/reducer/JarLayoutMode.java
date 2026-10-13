package com.yourorg.jmoa.plugin.reducer;

import java.util.Locale;

public enum JarLayoutMode {
    CURRENT("current"),
    STABLE_RAW_COPY("stable-raw-copy");

    private final String propertyValue;

    JarLayoutMode(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String propertyValue() {
        return propertyValue;
    }

    public static JarLayoutMode parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (JarLayoutMode mode : values()) {
            if (mode.propertyValue.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported jmoa.reducer.jarLayout: " + value);
    }
}
