package com.yourorg.jmoa.plugin.modec;

import com.yourorg.jmoa.plugin.ClassRootDescriptor;
import com.yourorg.jmoa.plugin.ClassRootKind;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeCClasspathWriterTest {

    @Test
    void writesRootsBeforeOriginalClasspathAndDeduplicatesEntries() throws Exception {
        Path tempDir = Files.createTempDirectory("jmoa-modec-cp");
        Path projectOutput = Files.createDirectories(tempDir.resolve("classes"));
        Path additionalDir = Files.createDirectories(tempDir.resolve("spring-aot"));
        Path expandedDir = Files.createDirectories(tempDir.resolve("expanded-spring"));
        Path dependencyJar = Files.createFile(tempDir.resolve("dependency.jar"));

        List<ClassRootDescriptor> roots = List.of(
            new ClassRootDescriptor(projectOutput.toFile(), true, ClassRootKind.PROJECT_OUTPUT),
            new ClassRootDescriptor(additionalDir.toFile(), false, ClassRootKind.ADDITIONAL_DIRECTORY),
            new ClassRootDescriptor(expandedDir.toFile(), false, ClassRootKind.EXPANDED_DEPENDENCY)
        );

        File output = new ModeCClasspathWriter().write(
            tempDir.toFile(),
            roots,
            List.of(
                projectOutput.toString(),
                additionalDir.toString(),
                dependencyJar.toString()
            )
        );

        List<String> lines = Files.readAllLines(output.toPath());
        assertEquals(
            List.of(
                projectOutput.toFile().getCanonicalPath(),
                additionalDir.toFile().getCanonicalPath(),
                expandedDir.toFile().getCanonicalPath(),
                dependencyJar.toFile().getCanonicalPath()
            ),
            lines
        );
        assertTrue(output.isFile());
    }
}
