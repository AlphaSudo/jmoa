package com.yourorg.jmoa.plugin.measure;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClassLoadDiffAnalyzer {

    public ClassLoadDiff analyze(ClassLoadInventory baseline, ClassLoadInventory candidate) {
        Set<String> baselineOnlyClasses = difference(baseline.allClasses(), candidate.allClasses());
        Set<String> candidateOnlyClasses = difference(candidate.allClasses(), baseline.allClasses());
        Set<String> sharedClasses = intersection(baseline.allClasses(), candidate.allClasses());
        Set<String> removedLambdaClasses = difference(baseline.lambdaClasses(), candidate.lambdaClasses());
        Set<String> addedJmoaToolClasses = difference(candidate.jmoaToolClasses(), baseline.jmoaToolClasses());
        Set<String> addedJmoaRuntimeLibClasses = difference(candidate.jmoaRuntimeLibClasses(), baseline.jmoaRuntimeLibClasses());
        Set<String> addedJmoaGeneratedOptimizationClasses = difference(candidate.jmoaGeneratedOptimizationClasses(), baseline.jmoaGeneratedOptimizationClasses());
        Set<String> addedJmoaGeneratedPackageAdapterClasses = difference(candidate.jmoaGeneratedPackageAdapterClasses(), baseline.jmoaGeneratedPackageAdapterClasses());
        Set<String> candidateNormalFrameworkClasses = normalFrameworkClasses(candidate.allClasses());
        Set<String> baselineNormalFrameworkClasses = normalFrameworkClasses(baseline.allClasses());
        Set<String> addedNormalFrameworkClasses = difference(candidateNormalFrameworkClasses, baselineNormalFrameworkClasses);
        Set<String> removedNormalFrameworkClasses = difference(baselineNormalFrameworkClasses, candidateNormalFrameworkClasses);

        return new ClassLoadDiff(
            baselineOnlyClasses,
            candidateOnlyClasses,
            sharedClasses,
            removedLambdaClasses,
            addedJmoaToolClasses,
            addedJmoaRuntimeLibClasses,
            addedJmoaGeneratedOptimizationClasses,
            addedJmoaGeneratedPackageAdapterClasses,
            addedNormalFrameworkClasses,
            removedNormalFrameworkClasses,
            packageCounts(addedNormalFrameworkClasses),
            packageCounts(removedNormalFrameworkClasses)
        );
    }

    private Set<String> normalFrameworkClasses(Set<String> classNames) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String className : classNames) {
            if (className.contains("$$Lambda")
                || className.startsWith("jmoa/")
                || className.startsWith("jmoa.")
                || className.contains("/JmoaPkgAdapters$")
                || className.contains(".JmoaPkgAdapters$")
                || className.contains("$JmoaPkgAdapters$")) {
                continue;
            }
            if (isFrameworkClass(className)) {
                result.add(className);
            }
        }
        return Set.copyOf(result);
    }

    private boolean isFrameworkClass(String className) {
        return className.startsWith("org/springframework/")
            || className.startsWith("org.springframework.")
            || className.startsWith("org/springframework/boot/")
            || className.startsWith("org.springframework.boot.")
            || className.startsWith("org/springframework/data/")
            || className.startsWith("org.springframework.data.")
            || className.startsWith("org/springframework/security/")
            || className.startsWith("org.springframework.security.")
            || className.startsWith("org/hibernate/")
            || className.startsWith("org.hibernate.")
            || className.startsWith("com/fasterxml/")
            || className.startsWith("com.fasterxml.");
    }

    private Map<String, Integer> packageCounts(Set<String> classes) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String className : classes.stream().sorted(Comparator.naturalOrder()).toList()) {
            String packageName = packageNameOf(className);
            counts.merge(packageName, 1, Integer::sum);
        }
        return counts;
    }

    private String packageNameOf(String className) {
        int slash = className.lastIndexOf('/');
        int dot = className.lastIndexOf('.');
        int split = Math.max(slash, dot);
        if (split <= 0) {
            return "<default>";
        }
        return className.substring(0, split);
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        LinkedHashSet<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return Set.copyOf(result);
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        LinkedHashSet<String> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return Set.copyOf(result);
    }

    public record ClassLoadDiff(
        Set<String> baselineOnlyClasses,
        Set<String> candidateOnlyClasses,
        Set<String> sharedClasses,
        Set<String> removedLambdaClasses,
        Set<String> addedJmoaToolClasses,
        Set<String> addedJmoaRuntimeLibClasses,
        Set<String> addedJmoaGeneratedOptimizationClasses,
        Set<String> addedJmoaGeneratedPackageAdapterClasses,
        Set<String> addedNormalFrameworkClasses,
        Set<String> removedNormalFrameworkClasses,
        Map<String, Integer> addedNormalFrameworkPackages,
        Map<String, Integer> removedNormalFrameworkPackages
    ) {
        public List<String> topAddedPackages(int limit) {
            return topPackages(addedNormalFrameworkPackages, limit);
        }

        public List<String> topRemovedPackages(int limit) {
            return topPackages(removedNormalFrameworkPackages, limit);
        }

        private List<String> topPackages(Map<String, Integer> counts, int limit) {
            return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(Math.max(0, limit))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
        }
    }
}
