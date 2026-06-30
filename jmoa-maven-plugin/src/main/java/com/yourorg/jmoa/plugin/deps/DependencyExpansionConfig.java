package com.yourorg.jmoa.plugin.deps;

import java.io.File;
import java.util.List;

public record DependencyExpansionConfig(
    File outputDirectory,
    boolean cleanOutputDirectory,
    List<String> includePrefixes,
    List<String> excludePrefixes,
    int maxExpandedClasses
) {

    public DependencyExpansionConfig {
        includePrefixes = includePrefixes == null ? List.of() : List.copyOf(includePrefixes);
        excludePrefixes = excludePrefixes == null ? List.of() : List.copyOf(excludePrefixes);
    }
}
