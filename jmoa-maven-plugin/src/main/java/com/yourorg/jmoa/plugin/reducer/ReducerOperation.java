package com.yourorg.jmoa.plugin.reducer;

import java.util.Locale;

public enum ReducerOperation {
    STRIP("strip"),
    REPACK_ONLY("repack-only");

    private final String propertyValue;

    ReducerOperation(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String propertyValue() {
        return propertyValue;
    }

    public static ReducerOperation parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (ReducerOperation operation : values()) {
            if (operation.propertyValue.equals(normalized)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("Unsupported jmoa.reducer.operation: " + value);
    }
}
