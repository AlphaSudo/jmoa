package com.yourorg.jmoa.plugin.synth;

import com.yourorg.jmoa.plugin.dedup.DeduplicationKey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SynthNamingStrategy {

    public static String getClassName(DeduplicationKey key) {
        String owner = key.implOwner();
        String simpleOwner = owner.substring(owner.lastIndexOf('/') + 1);

        String cleanOwner = sanitize(simpleOwner);
        String cleanName = sanitize(key.implName());

        String hash = sha256Hex4(key);
        return "LF_" + cleanOwner + "_" + cleanName + "_" + hash;
    }

    public static String getInternalName(DeduplicationKey key, String targetPackageInternal) {
        if (targetPackageInternal == null || targetPackageInternal.isEmpty()) {
            return "com/yourorg/jmoa/gen/" + getClassName(key);
        }
        String pkg = targetPackageInternal.replace('.', '/');
        if (!pkg.endsWith("/")) {
            pkg += "/";
        }
        return pkg + getClassName(key);
    }

    private static String sanitize(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isJavaIdentifierPart(c)) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String res = sb.toString();
        if (res.isEmpty() || !Character.isJavaIdentifierStart(res.charAt(0))) {
            res = "X" + res;
        }
        return res;
    }

    private static String sha256Hex4(DeduplicationKey key) {
        try {
            String keyStr = key.samInterfaceInternalName() + "|"
                    + key.samMethodDescriptor() + "|"
                    + key.implTag() + "|"
                    + key.implOwner() + "|"
                    + key.implName() + "|"
                    + key.implDesc() + "|"
                    + key.instantiatedMethodType();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(keyStr.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 2; i++) { // 2 bytes = 4 hex chars
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
