package com.yourorg.jmoa.plugin.deps;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class DependencyExpander {

    private final Log log;

    public DependencyExpander(Log log) {
        this.log = log;
    }

    public DependencyExpansionResult expand(
        Iterable<Artifact> artifacts,
        DependencyExpansionConfig config
    ) throws IOException {
        File outputDirectory = config.outputDirectory().getCanonicalFile();
        if (config.cleanOutputDirectory() && outputDirectory.exists()) {
            deleteRecursively(outputDirectory.toPath());
        }
        Files.createDirectories(outputDirectory.toPath());

        DependencyPathFilter pathFilter = new DependencyPathFilter(
            config.includePrefixes(),
            config.excludePrefixes()
        );

        List<Artifact> sortedArtifacts = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            sortedArtifacts.add(artifact);
        }
        sortedArtifacts.sort(Comparator.comparing(artifact -> DependencyCoordinate.fromArtifact(artifact).displayName()));

        List<ExpandedDependencyRoot> roots = new ArrayList<>();
        int jarsSeen = 0;
        int jarsExpanded = 0;
        int jarsSkipped = 0;
        int totalClasses = 0;

        for (Artifact artifact : sortedArtifacts) {
            File artifactFile = artifact.getFile();
            if (artifactFile == null || !artifactFile.isFile()) {
                continue;
            }

            jarsSeen++;
            DependencyCoordinate coordinate = DependencyCoordinate.fromArtifact(artifact);
            if (!pathFilter.shouldExpand(coordinate, artifactFile)) {
                jarsSkipped++;
                continue;
            }

            int remainingBudget = config.maxExpandedClasses() - totalClasses;
            if (remainingBudget <= 0) {
                throw new IOException("JMOA dependency expansion exceeded maxExpandedClasses=" + config.maxExpandedClasses());
            }

            File rootDirectory = new File(outputDirectory, coordinate.safeDirectoryName()).getCanonicalFile();
            int classCount = expandJarClassesOnly(artifactFile, rootDirectory.toPath(), remainingBudget);
            if (classCount == 0) {
                jarsSkipped++;
                continue;
            }

            jarsExpanded++;
            totalClasses += classCount;
            roots.add(new ExpandedDependencyRoot(coordinate, artifactFile.getCanonicalFile(), rootDirectory, classCount));
            log.info("JMOA expanded dependency: " + coordinate.displayName()
                + " -> " + rootDirectory.getAbsolutePath()
                + " (" + classCount + " classes)");
        }

        log.info("JMOA dependency expansion summary: jarsSeen=" + jarsSeen
            + ", jarsExpanded=" + jarsExpanded
            + ", jarsSkipped=" + jarsSkipped
            + ", totalClasses=" + totalClasses);

        return new DependencyExpansionResult(
            outputDirectory,
            roots,
            jarsSeen,
            jarsExpanded,
            jarsSkipped,
            totalClasses
        );
    }

    private int expandJarClassesOnly(File jarFile, Path outputRoot, int remainingBudget) throws IOException {
        Files.createDirectories(outputRoot);
        int classCount = 0;

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                if (entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                if (classCount >= remainingBudget) {
                    throw new IOException("JMOA dependency expansion exceeded maxExpandedClasses while extracting " + jarFile.getAbsolutePath());
                }

                Path targetPath = safeResolve(outputRoot, entry.getName());
                Files.createDirectories(targetPath.getParent());
                try (InputStream inputStream = jar.getInputStream(entry)) {
                    Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                classCount++;
            }
        }

        return classCount;
    }

    private Path safeResolve(Path root, String entryName) throws IOException {
        Path resolved = root.resolve(entryName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Unsafe jar entry path during dependency expansion: " + entryName);
        }
        return resolved;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(current -> {
                    try {
                        Files.deleteIfExists(current);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }
}
