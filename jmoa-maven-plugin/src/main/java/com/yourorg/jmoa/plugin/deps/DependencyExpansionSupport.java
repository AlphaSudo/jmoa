package com.yourorg.jmoa.plugin.deps;

import com.yourorg.jmoa.plugin.JmoaExecutionMode;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public final class DependencyExpansionSupport {

    private DependencyExpansionSupport() {
    }

    public static DependencyExpansionResult maybeExpand(
        MavenProject project,
        JmoaExecutionMode mode,
        boolean expandDependencies,
        File expandedDepsDir,
        String expandIncludes,
        String expandExcludes,
        boolean cleanExpandedDeps,
        int maxExpandedClasses,
        DependencyExpander expander
    ) throws Exception {
        if (mode != JmoaExecutionMode.MODE_C || !expandDependencies) {
            return DependencyExpansionResult.disabled(expandedDepsDir);
        }

        DependencyExpansionConfig config = new DependencyExpansionConfig(
            expandedDepsDir,
            cleanExpandedDeps,
            parseCsv(expandIncludes),
            parseCsv(expandExcludes),
            maxExpandedClasses
        );
        return expander.expand(project.getArtifacts(), config);
    }

    public static List<File> expandedRootDirectories(DependencyExpansionResult expansionResult) {
        return expansionResult.roots().stream()
            .map(ExpandedDependencyRoot::expandedRoot)
            .toList();
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }
}
