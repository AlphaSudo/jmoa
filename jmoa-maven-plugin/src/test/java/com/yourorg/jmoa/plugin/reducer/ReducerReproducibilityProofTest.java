package com.yourorg.jmoa.plugin.reducer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T5: real two-build reproducibility proof. Two clean reducer builds into
 * distinct absolute directories with different wall-clock times must produce
 * byte-identical JARs under repack-only + stable-raw-copy, and the proof must
 * detect genuinely different outputs.
 */
class ReducerReproducibilityProofTest {

    @TempDir
    Path tempDir;

    @Test
    void twoCleanBuildsInDistinctDirectoriesAreByteIdentical() throws Exception {
        Path input = tempDir.resolve("input");
        Files.createDirectories(input);
        writeJar(input.resolve("lib-a.jar"), "META-INF/config/one.properties", "META-INF/config/two.properties");
        writeJar(input.resolve("lib-b.jar"), "META-INF/config/three.properties");

        ReducerReproducibilityProof.ProofRecord proof =
                ReducerReproducibilityProof.prove(input, true, "repack-only", "stable-raw-copy");

        assertTrue(proof.passed(), () -> "differences: " + proof.differingEntries());
        assertEquals(2, proof.jarCount());
        assertTrue(proof.entryCount() >= 3);
        assertTrue(proof.entryOrderIdentical());
        assertTrue(proof.entryMetadataIdentical());
        assertTrue(proof.allBytesIdentical());
        assertFalse(proof.buildARoot().equals(proof.buildBRoot()));
        assertTrue(proof.inputTreeSha256().matches("^[0-9A-F]{64}$"));
        assertEquals(2, proof.jars().size());
        assertTrue(proof.jars().stream().allMatch(ReducerReproducibilityProof.JarProof::entryMetadataIdentical));
    }

    @Test
    void proofDetectsGenuinelyDifferentOutputs() throws Exception {
        Path buildA = tempDir.resolve("build-a");
        Path buildB = tempDir.resolve("build-b");
        Files.createDirectories(buildA);
        Files.createDirectories(buildB);
        writeJar(buildA.resolve("lib.jar"), "example/One.class");
        writeJar(buildB.resolve("lib.jar"), "example/One.class", "example/Extra.class");

        ReducerReproducibilityProof.ProofRecord proof = ReducerReproducibilityProof.compare(buildA, buildB);
        assertFalse(proof.passed());
        assertTrue(proof.differingEntries().stream().anyMatch(d -> d.contains("entry order/set differs")
                || d.contains("jar bytes differ")));
    }

    @Test
    void unsupportedRawZipFieldsAreExplicitlyModeled() {
        List<String> fields = ReducerReproducibilityProof.UNSUPPORTED_RAW_ZIP_FIELDS;
        assertTrue(fields.size() >= 5, "the unsupported-field model must be explicit, not empty");
        assertTrue(fields.stream().anyMatch(f -> f.contains("extra-field")));
        assertTrue(fields.stream().anyMatch(f -> f.contains("ZIP64")));
    }

    private static void writeJar(Path target, String... entries) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target))) {
            int ordinal = 0;
            for (String entry : entries) {
                JarEntry jarEntry = new JarEntry(entry);
                jarEntry.setTime(1700000000000L + (ordinal++ * 60000L));
                out.putNextEntry(jarEntry);
                out.write(classLikeBytes(entry));
                out.closeEntry();
            }
        }
    }

    private static byte[] classLikeBytes(String name) {
        // Content need not be a real classfile for layout reproducibility.
        byte[] bytes = new byte[64 + name.length()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 31 + name.length());
        }
        return bytes;
    }
}
