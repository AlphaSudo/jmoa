package com.example.direct;

import java.util.Map;

public final class DirectSemanticFixture {
    public DirectSemanticFixture() { }

    public static String requireValue(String value) {
        if ("reject".equals(value)) {
            throw new IllegalArgumentException("fixture rejection");
        }
        return value;
    }

    public static void store(Map<String, Object> target, Object value) {
        target.put("fixture", value);
    }
}
