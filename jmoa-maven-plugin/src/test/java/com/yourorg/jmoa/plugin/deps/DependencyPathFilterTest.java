package com.yourorg.jmoa.plugin.deps;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyPathFilterTest {

    @Test
    void includesMatchingSpringDependency() {
        DependencyPathFilter filter = new DependencyPathFilter(List.of("spring"), List.of());

        assertTrue(filter.shouldExpand(
            new DependencyCoordinate("org.springframework", "spring-context", "6.2.0", null),
            new File("spring-context-6.2.0.jar")
        ));
    }

    @Test
    void excludesMatchingDependencyBeforeInclude() {
        DependencyPathFilter filter = new DependencyPathFilter(List.of("spring"), List.of("context"));

        assertFalse(filter.shouldExpand(
            new DependencyCoordinate("org.springframework", "spring-context", "6.2.0", null),
            new File("spring-context-6.2.0.jar")
        ));
    }

    @Test
    void ignoresNonJarFiles() {
        DependencyPathFilter filter = new DependencyPathFilter(List.of(), List.of());

        assertFalse(filter.shouldExpand(
            new DependencyCoordinate("org.springframework", "spring-context", "6.2.0", null),
            new File("spring-context-6.2.0.txt")
        ));
    }
}
