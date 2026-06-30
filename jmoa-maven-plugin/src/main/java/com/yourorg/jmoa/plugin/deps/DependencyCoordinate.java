package com.yourorg.jmoa.plugin.deps;

import org.apache.maven.artifact.Artifact;

public record DependencyCoordinate(
    String groupId,
    String artifactId,
    String version,
    String classifier
) {

    public static DependencyCoordinate fromArtifact(Artifact artifact) {
        return new DependencyCoordinate(
            artifact.getGroupId(),
            artifact.getArtifactId(),
            artifact.getVersion(),
            artifact.getClassifier()
        );
    }

    public String displayName() {
        if (classifier == null || classifier.isBlank()) {
            return groupId + ":" + artifactId + ":" + version;
        }
        return groupId + ":" + artifactId + ":" + version + ":" + classifier;
    }

    public String safeDirectoryName() {
        StringBuilder builder = new StringBuilder()
            .append(sanitize(groupId))
            .append("__")
            .append(sanitize(artifactId))
            .append("__")
            .append(sanitize(version));
        if (classifier != null && !classifier.isBlank()) {
            builder.append("__").append(sanitize(classifier));
        }
        return builder.toString();
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
