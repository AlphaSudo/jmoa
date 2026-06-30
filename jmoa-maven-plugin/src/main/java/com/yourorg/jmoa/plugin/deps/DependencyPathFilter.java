package com.yourorg.jmoa.plugin.deps;

import java.io.File;
import java.util.List;
import java.util.Locale;

public final class DependencyPathFilter {

    private final List<String> includePrefixes;
    private final List<String> excludePrefixes;

    public DependencyPathFilter(List<String> includePrefixes, List<String> excludePrefixes) {
        this.includePrefixes = normalize(includePrefixes);
        this.excludePrefixes = normalize(excludePrefixes);
    }

    public boolean shouldExpand(DependencyCoordinate coordinate, File jarFile) {
        String jarName = jarFile.getName().toLowerCase(Locale.ROOT);
        if (!jarName.endsWith(".jar")) {
            return false;
        }

        String coordinateText = coordinate.displayName().toLowerCase(Locale.ROOT);
        String artifactId = coordinate.artifactId().toLowerCase(Locale.ROOT);
        String groupId = coordinate.groupId().toLowerCase(Locale.ROOT);

        for (String exclude : excludePrefixes) {
            if (matches(exclude, jarName, coordinateText, artifactId, groupId)) {
                return false;
            }
        }

        if (includePrefixes.isEmpty()) {
            return true;
        }

        for (String include : includePrefixes) {
            if (matches(include, jarName, coordinateText, artifactId, groupId)) {
                return true;
            }
        }

        return false;
    }

    private static boolean matches(
        String token,
        String jarName,
        String coordinateText,
        String artifactId,
        String groupId
    ) {
        return jarName.contains(token)
            || coordinateText.contains(token)
            || artifactId.contains(token)
            || groupId.contains(token);
    }

    private static List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
            .filter(value -> !value.isBlank())
            .toList();
    }
}
