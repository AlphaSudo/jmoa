package com.yourorg.jmoa.plugin.modec;

import com.yourorg.jmoa.plugin.ClassRootDescriptor;
import com.yourorg.jmoa.plugin.ClassRootKind;
import com.yourorg.jmoa.plugin.deps.ExpandedDependencyRoot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModeCOptimizedClasspathWriter {

    public File write(
        File targetDirectory,
        List<ClassRootDescriptor> roots,
        List<String> runtimeClasspathElements,
        List<ExpandedDependencyRoot> expandedRoots,
        OptimizedDependencyJarPackagingResult packagingResult
    ) throws IOException {
        return write(
            targetDirectory,
            roots,
            runtimeClasspathElements,
            expandedRoots,
            packagingResult,
            Set.of()
        ).classpathFile();
    }

    public ModeCOptimizedClasspathResult write(
        File targetDirectory,
        List<ClassRootDescriptor> roots,
        List<String> runtimeClasspathElements,
        List<ExpandedDependencyRoot> expandedRoots,
        OptimizedDependencyJarPackagingResult packagingResult,
        Set<String> hybridOverlayCoordinates
    ) throws IOException {
        Files.createDirectories(targetDirectory.toPath());
        File output = new File(targetDirectory, "jmoa-mode-c-optimized-jars.classpath.txt");
        Set<String> hybridCoordinates = hybridOverlayCoordinates == null ? Set.of() : hybridOverlayCoordinates;

        Map<String, String> originalJarToOptimizedJar = new LinkedHashMap<>();
        for (OptimizedDependencyJarArtifact artifact : packagingResult.artifacts()) {
            originalJarToOptimizedJar.put(
                artifact.originalJar().getCanonicalFile().getAbsolutePath(),
                artifact.optimizedJar().getCanonicalFile().getAbsolutePath()
            );
        }

        Map<String, ExpandedDependencyRoot> originalJarToHybridRoot = new LinkedHashMap<>();
        for (ExpandedDependencyRoot root : expandedRoots) {
            if (!hybridCoordinates.contains(root.coordinate().displayName())) {
                continue;
            }
            originalJarToHybridRoot.put(
                root.originalJar().getCanonicalFile().getAbsolutePath(),
                root
            );
        }

        Set<String> expandedRootPaths = new LinkedHashSet<>();
        for (ExpandedDependencyRoot root : expandedRoots) {
            expandedRootPaths.add(root.expandedRoot().getCanonicalFile().getAbsolutePath());
        }

        Set<String> orderedEntries = new LinkedHashSet<>();
        for (ClassRootDescriptor root : roots) {
            if (root.kind() == ClassRootKind.EXPANDED_DEPENDENCY) {
                continue;
            }
            addIfPresent(orderedEntries, root.rootDirectory());
        }
        for (String classpathElement : runtimeClasspathElements) {
            File entry = new File(classpathElement);
            if (!entry.exists()) {
                continue;
            }
            String canonicalPath = entry.getCanonicalFile().getAbsolutePath();
            if (expandedRootPaths.contains(canonicalPath)) {
                continue;
            }
            ExpandedDependencyRoot hybridRoot = originalJarToHybridRoot.get(canonicalPath);
            if (hybridRoot != null) {
                orderedEntries.add(hybridRoot.expandedRoot().getCanonicalFile().getAbsolutePath());
                orderedEntries.add(canonicalPath);
                continue;
            }
            String optimizedJar = originalJarToOptimizedJar.get(canonicalPath);
            if (optimizedJar != null) {
                orderedEntries.add(optimizedJar);
            } else {
                orderedEntries.add(canonicalPath);
            }
        }

        List<String> entries = new ArrayList<>(orderedEntries);
        Files.write(output.toPath(), entries);
        List<HybridOverlayClasspathEntry> overlayEntries = originalJarToHybridRoot.values().stream()
            .map(root -> new HybridOverlayClasspathEntry(root.coordinate(), root.expandedRoot(), root.originalJar()))
            .toList();
        HybridPackagingSummary summary = new HybridPackagingSummary(
            !hybridCoordinates.isEmpty(),
            new ArrayList<>(hybridCoordinates),
            overlayEntries,
            overlayEntries.size(),
            countContaining(entries, "jmoa-optimized-libs"),
            overlayEntries.size()
        );
        return new ModeCOptimizedClasspathResult(output, summary);
    }

    private void addIfPresent(Set<String> orderedEntries, File entry) throws IOException {
        if (entry == null || !entry.exists()) {
            return;
        }
        orderedEntries.add(entry.getCanonicalFile().getAbsolutePath());
    }

    private int countContaining(List<String> entries, String value) {
        int count = 0;
        for (String entry : entries) {
            if (entry.contains(value)) {
                count++;
            }
        }
        return count;
    }
}
