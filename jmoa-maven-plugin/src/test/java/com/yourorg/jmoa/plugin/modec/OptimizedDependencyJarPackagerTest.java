package com.yourorg.jmoa.plugin.modec;

import com.yourorg.jmoa.plugin.deps.DependencyCoordinate;
import com.yourorg.jmoa.plugin.deps.DependencyExpansionResult;
import com.yourorg.jmoa.plugin.deps.ExpandedDependencyRoot;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizedDependencyJarPackagerTest {

    @Test
    void packagesOptimizedJarWithOverlayAndResourcePreservation() throws Exception {
        Path tempDir = Files.createTempDirectory("jmoa-optimized-jars");
        Path originalJar = tempDir.resolve("spring-core-1.0.jar");
        Path expandedRoot = Files.createDirectories(tempDir.resolve("expanded"));
        Path outputDir = tempDir.resolve("optimized-libs");

        createJar(
            originalJar,
            List.of(
                jarEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"),
                jarEntry("META-INF/spring/example.factories", "factory=true"),
                jarEntry("META-INF/TEST.SF", "signature"),
                jarEntry("org/example/Foo.class", "original-foo"),
                jarEntry("org/example/Bar.class", "original-bar")
            )
        );
        writeFile(expandedRoot.resolve("org/example/Foo.class"), "rewritten-foo");
        writeFile(expandedRoot.resolve("org/example/Foo$JmoaPkgAdapters$S0.class"), "generated-adapter");
        writeFile(expandedRoot.resolve("org/example/Bar.class"), "original-bar");

        DependencyExpansionResult expansionResult = new DependencyExpansionResult(
            expandedRoot.getParent().toFile(),
            List.of(new ExpandedDependencyRoot(
                new DependencyCoordinate("org.springframework", "spring-core", "1.0", null),
                originalJar.toFile(),
                expandedRoot.toFile(),
                3
            )),
            1,
            1,
            0,
            3
        );

        OptimizedDependencyJarPackagingResult result = new OptimizedDependencyJarPackager(new SystemStreamLog())
            .packageDependencies(expansionResult, outputDir.toFile());

        assertEquals(1, result.artifacts().size());
        OptimizedDependencyJarArtifact artifact = result.artifacts().get(0);
        assertTrue(artifact.optimizedJar().isFile());
        assertEquals(1, artifact.rewrittenClasses());
        assertEquals(1, artifact.unchangedClasses());
        assertEquals(1, artifact.generatedAdapters());
        assertEquals(2, artifact.copiedResources());
        assertEquals(1, artifact.removedSignatures());

        try (JarFile jarFile = new JarFile(artifact.optimizedJar())) {
            assertEquals("rewritten-foo", readEntry(jarFile, "org/example/Foo.class"));
            assertEquals("original-bar", readEntry(jarFile, "org/example/Bar.class"));
            assertEquals("generated-adapter", readEntry(jarFile, "org/example/Foo$JmoaPkgAdapters$S0.class"));
            assertEquals("factory=true", readEntry(jarFile, "META-INF/spring/example.factories"));
            assertNotNull(jarFile.getJarEntry("META-INF/MANIFEST.MF"));
            assertFalse(jarFile.stream().anyMatch(entry -> entry.getName().endsWith(".SF")));
        }
    }

    @Test
    void skipsPackagingWhenExpandedDependencyHasNoRewrittenClassesOrGeneratedAdapters() throws Exception {
        Path tempDir = Files.createTempDirectory("jmoa-optimized-jars-skip");
        Path originalJar = tempDir.resolve("spring-core-1.0.jar");
        Path expandedRoot = Files.createDirectories(tempDir.resolve("expanded"));
        Path outputDir = tempDir.resolve("optimized-libs");

        createJar(
            originalJar,
            List.of(
                jarEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"),
                jarEntry("org/example/Foo.class", "original-foo")
            )
        );
        writeFile(expandedRoot.resolve("org/example/Foo.class"), "original-foo");

        DependencyExpansionResult expansionResult = new DependencyExpansionResult(
            expandedRoot.getParent().toFile(),
            List.of(new ExpandedDependencyRoot(
                new DependencyCoordinate("org.springframework", "spring-core", "1.0", null),
                originalJar.toFile(),
                expandedRoot.toFile(),
                1
            )),
            1,
            1,
            0,
            1
        );

        OptimizedDependencyJarPackagingResult result = new OptimizedDependencyJarPackager(new SystemStreamLog())
            .packageDependencies(expansionResult, outputDir.toFile());

        assertTrue(result.artifacts().isEmpty());
        assertFalse(Files.exists(outputDir.resolve("spring-core-1.0-jmoa.jar")));
    }

    @Test
    void skipsHybridOverlayCoordinatesEvenWhenTheyHaveRewrites() throws Exception {
        Path tempDir = Files.createTempDirectory("jmoa-optimized-jars-hybrid-skip");
        Path originalJar = tempDir.resolve("spring-core-1.0.jar");
        Path expandedRoot = Files.createDirectories(tempDir.resolve("expanded"));
        Path outputDir = tempDir.resolve("optimized-libs");
        DependencyCoordinate coordinate = new DependencyCoordinate("org.springframework", "spring-core", "1.0", null);

        createJar(
            originalJar,
            List.of(jarEntry("org/example/Foo.class", "original-foo"))
        );
        writeFile(expandedRoot.resolve("org/example/Foo.class"), "rewritten-foo");

        DependencyExpansionResult expansionResult = new DependencyExpansionResult(
            expandedRoot.getParent().toFile(),
            List.of(new ExpandedDependencyRoot(coordinate, originalJar.toFile(), expandedRoot.toFile(), 1)),
            1,
            1,
            0,
            1
        );

        OptimizedDependencyJarPackagingResult result = new OptimizedDependencyJarPackager(new SystemStreamLog())
            .packageDependencies(expansionResult, outputDir.toFile(), Set.of(coordinate.displayName()));

        assertTrue(result.artifacts().isEmpty());
        assertFalse(Files.exists(outputDir.resolve("spring-core-1.0-jmoa.jar")));
    }

    private static void createJar(Path jarPath, List<JarContent> entries) throws Exception {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            for (JarContent content : entries) {
                JarEntry entry = new JarEntry(content.name());
                jos.putNextEntry(entry);
                jos.write(content.content().getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }
    }

    private static void writeFile(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String readEntry(JarFile jarFile, String entryName) throws Exception {
        try (var inputStream = jarFile.getInputStream(jarFile.getJarEntry(entryName))) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JarContent jarEntry(String name, String content) {
        return new JarContent(name, content);
    }

    private record JarContent(String name, String content) {
    }
}
