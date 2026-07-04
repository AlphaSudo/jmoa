package com.yourorg.jmoa.plugin.reducer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class JarSafetyInspector {

    public JarSafetyAssessment assess(JarFile jar) throws IOException {
        boolean signed = false;
        boolean hasVersionedEntries = false;
        boolean multiReleaseManifest = false;
        boolean sealed = false;

        Manifest manifest = jar.getManifest();
        if (manifest != null) {
            Attributes main = manifest.getMainAttributes();
            multiReleaseManifest = "true".equalsIgnoreCase(main.getValue("Multi-Release"));
            sealed = "true".equalsIgnoreCase(main.getValue("Sealed"));
            for (Attributes attributes : manifest.getEntries().values()) {
                if ("true".equalsIgnoreCase(attributes.getValue("Sealed"))) {
                    sealed = true;
                    break;
                }
            }
        }

        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            String upper = name.toUpperCase(Locale.ROOT);
            if (upper.startsWith("META-INF/")
                && (upper.endsWith(".SF")
                    || upper.endsWith(".RSA")
                    || upper.endsWith(".DSA")
                    || upper.endsWith(".EC"))) {
                signed = true;
            }
            if (upper.startsWith("META-INF/VERSIONS/")) {
                hasVersionedEntries = true;
            }
        }

        boolean multiRelease = multiReleaseManifest || hasVersionedEntries;
        String skipReason = null;
        if (signed) {
            skipReason = "SKIPPED_SIGNED_JAR";
        } else if (multiRelease) {
            skipReason = "SKIPPED_MULTI_RELEASE_JAR";
        } else if (sealed) {
            skipReason = "SKIPPED_SEALED_JAR";
        }
        return new JarSafetyAssessment(signed, multiRelease, sealed, skipReason);
    }

    public static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (FileInputStream input = new FileInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                out.append(String.format("%02x", value));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
