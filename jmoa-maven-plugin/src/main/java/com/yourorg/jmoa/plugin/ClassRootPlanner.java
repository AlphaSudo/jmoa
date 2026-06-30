package com.yourorg.jmoa.plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ClassRootPlanner {

    public List<ClassRootDescriptor> planRoots(
        File projectOutputDirectory,
        List<String> compileClasspathElements,
        List<File> additionalClassDirectories,
        List<File> expandedDependencyRoots,
        JmoaExecutionMode mode,
        Consumer<String> logger
    ) throws IOException {
        Map<String, ClassRootDescriptor> orderedRoots = new LinkedHashMap<>();
        orderedRoots.put(
            projectOutputDirectory.getCanonicalPath(),
            new ClassRootDescriptor(projectOutputDirectory.getCanonicalFile(), true, ClassRootKind.PROJECT_OUTPUT)
        );

        if (additionalClassDirectories != null) {
            for (File directory : additionalClassDirectories) {
                if (directory == null || !directory.exists() || !directory.isDirectory()) {
                    continue;
                }
                File canonicalDirectory = directory.getCanonicalFile();
                orderedRoots.putIfAbsent(
                    canonicalDirectory.getCanonicalPath(),
                    new ClassRootDescriptor(
                        canonicalDirectory,
                        canonicalDirectory.equals(projectOutputDirectory.getCanonicalFile()),
                        ClassRootKind.ADDITIONAL_DIRECTORY
                    )
                );
            }
        }

        if (mode == JmoaExecutionMode.MODE_C && expandedDependencyRoots != null) {
            for (File directory : expandedDependencyRoots) {
                if (directory == null || !directory.exists() || !directory.isDirectory()) {
                    continue;
                }
                File canonicalDirectory = directory.getCanonicalFile();
                orderedRoots.putIfAbsent(
                    canonicalDirectory.getCanonicalPath(),
                    new ClassRootDescriptor(
                        canonicalDirectory,
                        canonicalDirectory.equals(projectOutputDirectory.getCanonicalFile()),
                        ClassRootKind.EXPANDED_DEPENDENCY
                    )
                );
            }
        }

        if (mode == JmoaExecutionMode.MODE_A) {
            return List.copyOf(orderedRoots.values());
        }

        for (String element : compileClasspathElements) {
            File file = new File(element);
            if (!file.exists()) {
                continue;
            }
            if (!file.isDirectory()) {
                logger.accept(mode + " skipping unsupported dependency classpath entry: " + file.getAbsolutePath());
                continue;
            }
            File canonicalFile = file.getCanonicalFile();
            orderedRoots.putIfAbsent(
                canonicalFile.getCanonicalPath(),
                new ClassRootDescriptor(
                    canonicalFile,
                    canonicalFile.equals(projectOutputDirectory.getCanonicalFile()),
                    canonicalFile.equals(projectOutputDirectory.getCanonicalFile())
                        ? ClassRootKind.PROJECT_OUTPUT
                        : ClassRootKind.ADDITIONAL_DIRECTORY
                )
            );
        }

        return new ArrayList<>(orderedRoots.values());
    }
}
