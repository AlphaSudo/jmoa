package com.yourorg.jmoa.plugin.reducer;

import java.util.Locale;

public enum ReducerEngine {
    ASM("asm"),
    RAW("raw");

    private final String propertyValue;

    ReducerEngine(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String propertyValue() {
        return propertyValue;
    }

    public static ReducerEngine parse(String value) {
        if (value == null || value.isBlank()) {
            return ASM;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ReducerEngine engine : values()) {
            if (engine.propertyValue.equals(normalized)) {
                return engine;
            }
        }
        throw new IllegalArgumentException("Unknown reducer engine: " + value);
    }
}
