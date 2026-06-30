package com.yourorg.jmoa.plugin;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassRootPlannerTest {

    @Test
    void modeAUsesOnlyProjectOutputDirectory() throws Exception {
        File projectOutput = Files.createTempDirectory("jmoa-mode-a").toFile();
        File dependencyDir = Files.createTempDirectory("jmoa-dep-a").toFile();

        List<ClassRootDescriptor> roots = new ClassRootPlanner().planRoots(
            projectOutput,
            List.of(projectOutput.getAbsolutePath(), dependencyDir.getAbsolutePath()),
            List.of(),
            List.of(),
            JmoaExecutionMode.MODE_A,
            ignored -> { }
        );

        assertEquals(1, roots.size());
        assertEquals(projectOutput.getCanonicalFile(), roots.getFirst().rootDirectory());
        assertTrue(roots.getFirst().projectOwned());
    }

    @Test
    void modeBIncludesDirectoryDependenciesAndSkipsJars() throws Exception {
        File projectOutput = Files.createTempDirectory("jmoa-mode-b").toFile();
        File dependencyDir = Files.createTempDirectory("jmoa-dep-b").toFile();
        File jarFile = File.createTempFile("jmoa-dep", ".jar");
        List<String> logs = new ArrayList<>();

        List<ClassRootDescriptor> roots = new ClassRootPlanner().planRoots(
            projectOutput,
            List.of(projectOutput.getAbsolutePath(), dependencyDir.getAbsolutePath(), jarFile.getAbsolutePath()),
            List.of(),
            List.of(),
            JmoaExecutionMode.MODE_B,
            logs::add
        );

        assertEquals(2, roots.size());
        assertEquals(projectOutput.getCanonicalFile(), roots.get(0).rootDirectory());
        assertEquals(dependencyDir.getCanonicalFile(), roots.get(1).rootDirectory());
        assertTrue(logs.stream().anyMatch(line -> line.contains("skipping unsupported dependency classpath entry")));
    }

    @Test
    void additionalClassDirectoriesAreIncludedBeforeModeBExpansion() throws Exception {
        File projectOutput = Files.createTempDirectory("jmoa-mode-extra").toFile();
        File additionalDir = Files.createTempDirectory("jmoa-extra-root").toFile();
        File dependencyDir = Files.createTempDirectory("jmoa-dep-extra").toFile();

        List<ClassRootDescriptor> roots = new ClassRootPlanner().planRoots(
            projectOutput,
            List.of(projectOutput.getAbsolutePath(), dependencyDir.getAbsolutePath()),
            List.of(additionalDir),
            List.of(),
            JmoaExecutionMode.MODE_A,
            ignored -> { }
        );

        assertEquals(2, roots.size());
        assertEquals(projectOutput.getCanonicalFile(), roots.get(0).rootDirectory());
        assertEquals(additionalDir.getCanonicalFile(), roots.get(1).rootDirectory());
    }

    @Test
    void modeCIncludesExpandedDependencyRoots() throws Exception {
        File projectOutput = Files.createTempDirectory("jmoa-mode-c").toFile();
        File additionalDir = Files.createTempDirectory("jmoa-mode-c-aot").toFile();
        File expandedRoot = Files.createTempDirectory("jmoa-mode-c-expanded").toFile();
        File dependencyDir = Files.createTempDirectory("jmoa-mode-c-dep").toFile();
        File jarFile = File.createTempFile("jmoa-mode-c-dep", ".jar");
        List<String> logs = new ArrayList<>();

        List<ClassRootDescriptor> roots = new ClassRootPlanner().planRoots(
            projectOutput,
            List.of(projectOutput.getAbsolutePath(), dependencyDir.getAbsolutePath(), jarFile.getAbsolutePath()),
            List.of(additionalDir),
            List.of(expandedRoot),
            JmoaExecutionMode.MODE_C,
            logs::add
        );

        assertEquals(4, roots.size());
        assertEquals(projectOutput.getCanonicalFile(), roots.get(0).rootDirectory());
        assertEquals(additionalDir.getCanonicalFile(), roots.get(1).rootDirectory());
        assertEquals(expandedRoot.getCanonicalFile(), roots.get(2).rootDirectory());
        assertEquals(dependencyDir.getCanonicalFile(), roots.get(3).rootDirectory());
        assertTrue(logs.stream().anyMatch(line -> line.contains("MODE_C skipping unsupported dependency classpath entry")));
    }
}
