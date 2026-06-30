package com.yourorg.jmoa.plugin.deps;

import java.io.File;
import java.util.List;

public record DependencyExpansionResult(
    File outputDirectory,
    List<ExpandedDependencyRoot> roots,
    int jarsSeen,
    int jarsExpanded,
    int jarsSkipped,
    int totalClassesExpanded
) {

    public DependencyExpansionResult {
        roots = roots == null ? List.of() : List.copyOf(roots);
    }

    public static DependencyExpansionResult disabled(File outputDirectory) {
        return new DependencyExpansionResult(outputDirectory, List.of(), 0, 0, 0, 0);
    }
}
