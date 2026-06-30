package com.yourorg.jmoa.plugin.measure;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassLoadDiffAnalyzerTest {

    @Test
    void computesAddedRemovedLambdaAndFrameworkClassSets() {
        ClassLoadInventory baseline = new ClassLoadInventory(
            Set.of(
                "example/Main",
                "example/Main$$Lambda/0x1",
                "org/springframework/context/Foo",
                "org/springframework/context/Foo$$Lambda/0x2"
            ),
            Set.of("example/Main$$Lambda/0x1", "org/springframework/context/Foo$$Lambda/0x2"),
            Set.of("example/Main$$Lambda/0x1"),
            Set.of("org/springframework/context/Foo$$Lambda/0x2"),
            Set.of("jmoa/tools/ModeCClasspathLauncher"),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of()
        );
        ClassLoadInventory candidate = new ClassLoadInventory(
            new LinkedHashSet<>(Set.of(
                "example/Main",
                "org/springframework/context/Foo",
                "jmoa/runtime/JmoaRuntime",
                "org/springframework/context/JmoaPkgAdapters$Foo$S0_deadbeef",
                "org/springframework/context/Bar"
            )),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of("jmoa/tools/ModeCClasspathLauncher"),
            Set.of("jmoa/runtime/JmoaRuntime"),
            Set.of("jmoa/runtime/JmoaRuntime"),
            Set.of("org/springframework/context/JmoaPkgAdapters$Foo$S0_deadbeef"),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of()
        );

        ClassLoadDiffAnalyzer.ClassLoadDiff diff = new ClassLoadDiffAnalyzer().analyze(baseline, candidate);

        assertEquals(Set.of("example/Main$$Lambda/0x1", "org/springframework/context/Foo$$Lambda/0x2"), diff.removedLambdaClasses());
        assertEquals(Set.of("jmoa/runtime/JmoaRuntime"), diff.addedJmoaGeneratedOptimizationClasses());
        assertEquals(Set.of("org/springframework/context/JmoaPkgAdapters$Foo$S0_deadbeef"), diff.addedJmoaGeneratedPackageAdapterClasses());
        assertEquals(Set.of("org/springframework/context/Bar"), diff.addedNormalFrameworkClasses());
        assertTrue(diff.topAddedPackages(5).contains("org/springframework/context=1"));
    }
}
