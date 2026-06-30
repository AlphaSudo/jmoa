package com.yourorg.jmoa.plugin.modec;

import com.yourorg.jmoa.plugin.deps.DependencyCoordinate;

import java.io.File;

public record HybridOverlayClasspathEntry(
    DependencyCoordinate coordinate,
    File expandedRoot,
    File originalJar
) {
}
