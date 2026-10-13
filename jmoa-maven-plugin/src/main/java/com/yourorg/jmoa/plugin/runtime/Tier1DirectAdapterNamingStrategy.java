package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.model.LambdaMeta;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Tier1DirectAdapterNamingStrategy {

    private Tier1DirectAdapterNamingStrategy() {
    }

    public static String internalName(LambdaMeta meta) {
        String erased = Tier2AdapterNamingStrategy.eraseSamMethodDesc(meta.samMethodTypeDesc());
        return "jmoa/runtime/JmoaDirectAdapters$" + shortHash(erased);
    }

    public static String fieldName(LambdaMeta meta) {
        return Tier2AdapterNamingStrategy.compactFieldName(meta);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                result.append(String.format("%02x", digest[i]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
