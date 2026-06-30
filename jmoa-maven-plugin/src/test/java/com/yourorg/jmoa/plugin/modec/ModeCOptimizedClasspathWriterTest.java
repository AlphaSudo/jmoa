package com.yourorg.jmoa.plugin.modec;

import com.yourorg.jmoa.plugin.ClassRootDescriptor;
import com.yourorg.jmoa.plugin.ClassRootKind;
import com.yourorg.jmoa.plugin.deps.DependencyCoordinate;
import com.yourorg.jmoa.plugin.deps.ExpandedDependencyRoot;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModeCOptimizedClasspathWriterTest {

    @Test
    void writesOptimizedJarsInsteadOfExpandedRootsAndOriginalJars() throws Exception {
        Path tempDir = Files.createTempDirectory("jmoa-modec-optimized-cp");
        Path projectOutput = Files.createDirectories(tempDir.resolve("classes"));
        Path aotDir = Files.createDirectories(tempDir.resolve("spring-aot"));
        Path expandedDir = Files.createDirectories(tempDir.resolve("expanded-spring-core"));
        Path originalJar = Files.createFile(tempDir.resolve("spring-core-1.0.jar"));
        Path optimizedJar = Files.createFile(tempDir.resolve("spring-core-1.0-jmoa.jar"));
        Path otherJar = Files.createFile(tempDir.resolve("other.jar"));

        List<ClassRootDescriptor> roots = List.of(
            new ClassRootDescriptor(projectOutput.toFile(), true, ClassRootKind.PROJECT_OUTPUT),
            new ClassRootDescriptor(aotDir.toFile(), false, ClassRootKind.ADDITIONAL_DIRECTORY),
            new ClassRootDescriptor(expandedDir.toFile(), false, ClassRootKind.EXPANDED_DEPENDENCY)
        );
        List<ExpandedDependencyRoot> expandedRoots = List.of(
            new ExpandedDependencyRoot(
                new DependencyCoordinate("org.springframework", "spring-core", "1.0", null),
                originalJar.toFile(),
                expandedDir.toFile(),
                2
            )
        );
        OptimizedDependencyJarPackagingResult packagingResult = new OptimizedDependencyJarPackagingResult(
            tempDir.resolve("optimized-libs").toFile(),
            List.of(new OptimizedDependencyJarArtifact(
                new DependencyCoordinate("org.springframework", "spring-core", "1.0", null),
                originalJar.toFile(),
                optimizedJar.toFile(),
                1,
                0,
                1,
                2,
                1
            ))
        );

        File output = new ModeCOptimizedClasspathWriter().write(
            tempDir.toFile(),
            roots,
            List.of(
                projectOutput.toString(),
                aotDir.toString(),
                expandedDir.toString(),
                originalJar.toString(),
                otherJar.toString()
            ),
            expandedRoots,
            packagingResult
        );

        assertEquals(
            List.of(
                projectOutput.toFile().getCanonicalPath(),
                aotDir.toFile().getCanonicalPath(),
                optimizedJar.toFile().getCanonicalPath(),
                otherJar.toFile().getCanonicalPath()
            ),
            Files.readAllLines(output.toPath())
        );
    }

    @Test
    void writesHybridOverlayCoordinateAsExpandedRootThenOriginalJar() throws Exception {
        Path tempDir = Files.createTempDirectory("jmoa-modec-hybrid-cp");
        Path projectOutput = Files.createDirectories(tempDir.resolve("classes"));
        Path expandedDir = Files.createDirectories(tempDir.resolve("expanded-spring-core"));
        Path originalJar = Files.createFile(tempDir.resolve("spring-core-1.0.jar"));
        Path optimizedJar = Files.createFile(tempDir.resolve("spring-core-1.0-jmoa.jar"));
        Path otherJar = Files.createFile(tempDir.resolve("other.jar"));

        DependencyCoordinate coordinate = new DependencyCoordinate("org.springframework", "spring-core", "1.0", null);
        List<ClassRootDescriptor> roots = List.of(
            new ClassRootDescriptor(projectOutput.toFile(), true, ClassRootKind.PROJECT_OUTPUT),
            new ClassRootDescriptor(expandedDir.toFile(), false, ClassRootKind.EXPANDED_DEPENDENCY)
        );
        List<ExpandedDependencyRoot> expandedRoots = List.of(
            new ExpandedDependencyRoot(coordinate, originalJar.toFile(), expandedDir.toFile(), 2)
        );
        OptimizedDependencyJarPackagingResult packagingResult = new OptimizedDependencyJarPackagingResult(
            tempDir.resolve("optimized-libs").toFile(),
            List.of(new OptimizedDependencyJarArtifact(
                coordinate,
                originalJar.toFile(),
                optimizedJar.toFile(),
                1,
                0,
                1,
                2,
                1
            ))
        );

        ModeCOptimizedClasspathResult result = new ModeCOptimizedClasspathWriter().write(
            tempDir.toFile(),
            roots,
            List.of(projectOutput.toString(), expandedDir.toString(), originalJar.toString(), otherJar.toString()),
            expandedRoots,
            packagingResult,
            Set.of(coordinate.displayName())
        );

        assertEquals(
            List.of(
                projectOutput.toFile().getCanonicalPath(),
                expandedDir.toFile().getCanonicalPath(),
                originalJar.toFile().getCanonicalPath(),
                otherJar.toFile().getCanonicalPath()
            ),
            Files.readAllLines(result.classpathFile().toPath())
        );
        assertEquals(true, result.hybridPackagingSummary().enabled());
        assertEquals(1, result.hybridPackagingSummary().overlayEntries().size());
        assertEquals(1, result.hybridPackagingSummary().expandedDependencyRuntimeEntries());
        assertEquals(0, result.hybridPackagingSummary().optimizedJarRuntimeEntries());
        assertEquals(1, result.hybridPackagingSummary().originalFallbackJarEntries());
    }
}
