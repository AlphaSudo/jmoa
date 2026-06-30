package com.yourorg.jmoa.plugin.measure;

import java.util.LinkedHashSet;
import java.util.Set;

public record ClassLoadInventory(
    Set<String> allClasses,
    Set<String> lambdaClasses,
    Set<String> applicationLambdaClasses,
    Set<String> frameworkLambdaClasses,
    Set<String> jmoaToolClasses,
    Set<String> jmoaRuntimeLibClasses,
    Set<String> jmoaGeneratedOptimizationClasses,
    Set<String> jmoaGeneratedPackageAdapterClasses,
    Set<String> jdkInternalClassfileClasses,
    Set<String> javaLangClassfileClasses,
    Set<String> springCoreClassReadingClasses,
    Set<String> unloadedClasses,
    Set<String> unloadedLambdaClasses,
    Set<String> unloadedApplicationLambdaClasses,
    Set<String> unloadedFrameworkLambdaClasses,
    Set<String> unloadedJmoaRuntimeLibClasses,
    Set<String> unloadedJmoaGeneratedOptimizationClasses,
    Set<String> unloadedJmoaGeneratedPackageAdapterClasses
) {

    public ClassLoadInventory {
        allClasses = copy(allClasses);
        lambdaClasses = copy(lambdaClasses);
        applicationLambdaClasses = copy(applicationLambdaClasses);
        frameworkLambdaClasses = copy(frameworkLambdaClasses);
        jmoaToolClasses = copy(jmoaToolClasses);
        jmoaRuntimeLibClasses = copy(jmoaRuntimeLibClasses);
        jmoaGeneratedOptimizationClasses = copy(jmoaGeneratedOptimizationClasses);
        jmoaGeneratedPackageAdapterClasses = copy(jmoaGeneratedPackageAdapterClasses);
        jdkInternalClassfileClasses = copy(jdkInternalClassfileClasses);
        javaLangClassfileClasses = copy(javaLangClassfileClasses);
        springCoreClassReadingClasses = copy(springCoreClassReadingClasses);
        unloadedClasses = copy(unloadedClasses);
        unloadedLambdaClasses = copy(unloadedLambdaClasses);
        unloadedApplicationLambdaClasses = copy(unloadedApplicationLambdaClasses);
        unloadedFrameworkLambdaClasses = copy(unloadedFrameworkLambdaClasses);
        unloadedJmoaRuntimeLibClasses = copy(unloadedJmoaRuntimeLibClasses);
        unloadedJmoaGeneratedOptimizationClasses = copy(unloadedJmoaGeneratedOptimizationClasses);
        unloadedJmoaGeneratedPackageAdapterClasses = copy(unloadedJmoaGeneratedPackageAdapterClasses);
    }

    public Set<String> jmoaGeneratedClasses() {
        LinkedHashSet<String> combined = new LinkedHashSet<>(jmoaGeneratedOptimizationClasses);
        combined.addAll(jmoaGeneratedPackageAdapterClasses);
        return combined;
    }

    public Set<String> unloadedJmoaGeneratedClasses() {
        LinkedHashSet<String> combined = new LinkedHashSet<>(unloadedJmoaGeneratedOptimizationClasses);
        combined.addAll(unloadedJmoaGeneratedPackageAdapterClasses);
        return combined;
    }

    private static Set<String> copy(Set<String> source) {
        return source == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(source));
    }
}
