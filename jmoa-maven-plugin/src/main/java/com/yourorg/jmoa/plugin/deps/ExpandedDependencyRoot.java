package com.yourorg.jmoa.plugin.deps;

import java.io.File;

public record ExpandedDependencyRoot(
    DependencyCoordinate coordinate,
    File originalJar,
    File expandedRoot,
    int classCount
) {
}
