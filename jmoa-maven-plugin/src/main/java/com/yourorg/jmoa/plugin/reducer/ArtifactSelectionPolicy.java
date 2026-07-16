package com.yourorg.jmoa.plugin.reducer;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;

final class ArtifactSelectionPolicy {

    private final List<PathMatcher> includes;
    private final List<PathMatcher> excludes;

    ArtifactSelectionPolicy(ReducerConfig config) {
        includes = matchers(config.artifactIncludes());
        excludes = matchers(config.artifactExcludes());
    }

    boolean isSelected(String artifactName) {
        Path name = Path.of(artifactName).getFileName();
        boolean included = includes.isEmpty() || includes.stream().anyMatch(matcher -> matcher.matches(name));
        return included && excludes.stream().noneMatch(matcher -> matcher.matches(name));
    }

    private static List<PathMatcher> matchers(String patterns) {
        if (patterns == null || patterns.isBlank()) {
            return List.of();
        }
        return Arrays.stream(patterns.split(","))
            .map(String::trim)
            .filter(pattern -> !pattern.isEmpty())
            .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
            .toList();
    }
}
