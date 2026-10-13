package com.yourorg.jmoa.plugin.modec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Track T11: the hybrid classpath origin manifest proves
 * expanded-root-before-fallback ordering, records every contender, and fails
 * closed on ambiguous protected resources, signed, sealed, or multi-release
 * JARs.
 */
class HybridOriginManifestTest {

    @TempDir
    Path tempDir;

    @Test
    void expandedRootWinsBeforeFallbackJarAndDuplicatesAreRecorded() throws Exception {
        Path expanded = dir("expanded-root");
        Path fallback = dir("fallback");
        write(expanded, "com/example/Widget.class", 1);
        write(expanded, "META-INF/services/com.example.Api", 7);
        write(fallback, "com/example/Widget.class", 1);          // identical shadow
        write(fallback, "com/example/Other.class", 2);
        write(fallback, "com/example/Shadowed.class", 3);

        Path rewrittenDifferent = dir("second-root");
        write(rewrittenDifferent, "com/example/Shadowed.class", 4); // differing ordinary class

        HybridOriginManifestGenerator.OriginManifest manifest =
                HybridOriginManifestGenerator.generate(List.of(expanded, fallback, rewrittenDifferent));

        assertEquals(3, manifest.originCount());
        var widget = entry(manifest, "com/example/Widget.class");
        assertEquals(0, widget.winner().classpathOrdinal(), "expanded root must win before fallback");
        assertTrue(widget.winner().sha256().matches("^[0-9A-F]{64}$"));
        assertEquals(2, widget.contenders().size());
        assertTrue(widget.duplicate());
        assertEquals("DUPLICATE_IDENTICAL_CONTENT", widget.disposition());

        var shadowed = entry(manifest, "com/example/Shadowed.class");
        assertEquals(1, shadowed.winner().classpathOrdinal(), "first-in-classpath shadowing for ordinary classes");
        assertEquals("WINNER_FIRST_CLASSPATH_SHADOWING", shadowed.disposition());

        var single = entry(manifest, "com/example/Other.class");
        assertEquals(1, single.winner().classpathOrdinal());
        assertTrue(!single.duplicate());
        assertEquals(3, manifest.origins().size());
        assertTrue(manifest.origins().stream().allMatch(origin -> origin.contentSha256().matches("^[0-9A-F]{64}$")));
    }

    @Test
    void differingServiceDescriptorsRefuseTheWholeManifest() throws Exception {
        Path rootA = dir("a");
        Path rootB = dir("b");
        write(rootA, "META-INF/services/com.example.Api", 10);
        write(rootB, "META-INF/services/com.example.Api", 20);

        IOException refusal = assertThrows(IOException.class,
                () -> HybridOriginManifestGenerator.generate(List.of(rootA, rootB)));
        assertTrue(refusal.getMessage().contains("ambiguous protected resource"));
        assertTrue(refusal.getMessage().contains("META-INF/services/com.example.Api"));
    }

    @Test
    void differingSpringFactoriesAndDuplicateModuleInfoRefuse() throws Exception {
        Path springA = dir("spring-a");
        Path springB = dir("spring-b");
        write(springA, "META-INF/spring.factories", 1);
        write(springB, "META-INF/spring.factories", 2);
        IOException springRefusal = assertThrows(IOException.class,
                () -> HybridOriginManifestGenerator.generate(List.of(springA, springB)));
        assertTrue(springRefusal.getMessage().contains("META-INF/spring.factories"));

        Path moduleA = dir("module-a");
        Path moduleB = dir("module-b");
        write(moduleA, "module-info.class", 1);
        write(moduleB, "module-info.class", 1);
        IOException moduleRefusal = assertThrows(IOException.class,
                () -> HybridOriginManifestGenerator.generate(List.of(moduleA, moduleB)));
        assertTrue(moduleRefusal.getMessage().contains("module-info.class"));
    }

    @Test
    void signedSealedAndMultiReleaseJarsRefuse() throws Exception {
        Path signedJar = tempDir.resolve("signed.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(signedJar))) {
            put(out, "com/example/Signed.class", 1);
            put(out, "META-INF/CODESIGN.SF", 2);
        }
        IOException signedRefusal = assertThrows(IOException.class,
                () -> HybridOriginManifestGenerator.generate(List.of(signedJar)));
        assertTrue(signedRefusal.getMessage().contains("signed JAR"));

        Manifest sealedManifest = new Manifest();
        sealedManifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        sealedManifest.getMainAttributes().putValue("Sealed", "true");
        Path sealedJar = tempDir.resolve("sealed.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(sealedJar), sealedManifest)) {
            put(out, "com/example/Sealed.class", 1);
        }
        IOException sealedRefusal = assertThrows(IOException.class,
                () -> HybridOriginManifestGenerator.generate(List.of(sealedJar)));
        assertTrue(sealedRefusal.getMessage().contains("sealed JAR"));

        Path multiReleaseJar = tempDir.resolve("multi-release.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(multiReleaseJar))) {
            put(out, "com/example/Base.class", 1);
            put(out, "META-INF/versions/17/com/example/Base.class", 2);
        }
        IOException mrRefusal = assertThrows(IOException.class,
                () -> HybridOriginManifestGenerator.generate(List.of(multiReleaseJar)));
        assertTrue(mrRefusal.getMessage().contains("multi-release JAR"));
    }

    private static HybridOriginManifestGenerator.LogicalEntry entry(
            HybridOriginManifestGenerator.OriginManifest manifest, String name) {
        return manifest.entries().stream()
                .filter(e -> e.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing entry " + name));
    }

    private Path dir(String name) throws IOException {
        Path path = tempDir.resolve(name);
        Files.createDirectories(path);
        return path;
    }

    private static void write(Path root, String name, int seed) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {(byte) seed, (byte) (seed * 3), 'x'});
    }

    private static void put(JarOutputStream out, String name, int seed) throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setTime(1700000000000L);
        out.putNextEntry(entry);
        out.write(new byte[] {(byte) seed, (byte) (seed * 3), 'x'});
        out.closeEntry();
    }
}
