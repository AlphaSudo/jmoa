package com.yourorg.jmoa.plugin.deps;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyExpanderTest {

    @Test
    void expandsOnlyMatchingJarClasses() throws Exception {
        File outputDir = Files.createTempDirectory("jmoa-expanded-deps").toFile();
        File springJar = createJar(
            "spring-context-6.2.0.jar",
            List.of(
                "org/springframework/context/SafeFixture.class",
                "META-INF/MANIFEST.MF",
                "org/springframework/context/notes.txt"
            )
        );
        File jacksonJar = createJar(
            "jackson-databind-2.18.2.jar",
            List.of("com/fasterxml/jackson/databind/UnsafeFixture.class")
        );

        DefaultArtifact springArtifact = new DefaultArtifact(
            "org.springframework",
            "spring-context",
            "6.2.0",
            "compile",
            "jar",
            null,
            new DefaultArtifactHandler("jar")
        );
        springArtifact.setFile(springJar);

        DefaultArtifact jacksonArtifact = new DefaultArtifact(
            "com.fasterxml.jackson.core",
            "jackson-databind",
            "2.18.2",
            "compile",
            "jar",
            null,
            new DefaultArtifactHandler("jar")
        );
        jacksonArtifact.setFile(jacksonJar);

        DependencyExpansionResult result = new DependencyExpander(new SystemStreamLog()).expand(
            List.of(springArtifact, jacksonArtifact),
            new DependencyExpansionConfig(
                outputDir,
                true,
                List.of("spring"),
                List.of("jackson"),
                100
            )
        );

        assertEquals(2, result.jarsSeen());
        assertEquals(1, result.jarsExpanded());
        assertEquals(1, result.jarsSkipped());
        assertEquals(1, result.totalClassesExpanded());
        assertEquals(1, result.roots().size());

        ExpandedDependencyRoot expandedRoot = result.roots().getFirst();
        assertTrue(new File(expandedRoot.expandedRoot(), "org/springframework/context/SafeFixture.class").isFile());
        assertFalse(new File(expandedRoot.expandedRoot(), "META-INF/MANIFEST.MF").exists());
    }

    private File createJar(String name, List<String> entries) throws IOException {
        File jarFile = File.createTempFile(name.replace(".jar", ""), ".jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarFile.toPath()))) {
            for (String entryName : entries) {
                JarEntry entry = new JarEntry(entryName);
                jar.putNextEntry(entry);
                if (!entryName.endsWith("/")) {
                    jar.write(("stub:" + entryName).getBytes(StandardCharsets.UTF_8));
                }
                jar.closeEntry();
            }
        }
        return jarFile;
    }
}
