package com.yourorg.jmoa.plugin.filter;

import com.yourorg.jmoa.plugin.JmoaExecutionMode;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyConfig;

import java.util.List;

public record LambdaFilterConfig(
    long hotInvocationThreshold,
    boolean requireObservedSite,
    boolean allowColdClassInference,
    List<String> frameworkPackageExclusions,
    JmoaExecutionMode executionMode,
    FrameworkSafetyConfig frameworkSafetyConfig,
    boolean disableTier1FrameworkSites,
    boolean disableTier2FrameworkSites
) {

    public LambdaFilterConfig {
        frameworkPackageExclusions = frameworkPackageExclusions == null
            ? List.of()
            : frameworkPackageExclusions.stream()
                .map(LambdaFilterConfig::normalizePrefix)
                .distinct()
                .toList();
        executionMode = executionMode == null ? JmoaExecutionMode.MODE_A : executionMode;
        frameworkSafetyConfig = frameworkSafetyConfig == null ? FrameworkSafetyConfig.defaults() : frameworkSafetyConfig;
    }

    public static LambdaFilterConfig defaults() {
        return new LambdaFilterConfig(
            10_000,
            true,
            true,
            List.of(
                "org/hibernate/",
                "com/fasterxml/",
                "jakarta/",
                "javax/",
                "reactor/",
                "kotlin/"
            ),
            JmoaExecutionMode.MODE_A,
            FrameworkSafetyConfig.defaults(),
            false,
            false
        );
    }

    public LambdaFilterConfig withHotInvocationThreshold(long hotInvocationThreshold) {
        return new LambdaFilterConfig(
            hotInvocationThreshold,
            requireObservedSite,
            allowColdClassInference,
            frameworkPackageExclusions,
            executionMode,
            frameworkSafetyConfig,
            disableTier1FrameworkSites,
            disableTier2FrameworkSites
        );
    }

    public LambdaFilterConfig withFrameworkPackageExclusions(List<String> frameworkPackageExclusions) {
        return new LambdaFilterConfig(
            hotInvocationThreshold,
            requireObservedSite,
            allowColdClassInference,
            frameworkPackageExclusions,
            executionMode,
            frameworkSafetyConfig,
            disableTier1FrameworkSites,
            disableTier2FrameworkSites
        );
    }

    public LambdaFilterConfig withExecutionMode(JmoaExecutionMode executionMode) {
        return new LambdaFilterConfig(
            hotInvocationThreshold,
            requireObservedSite,
            allowColdClassInference,
            frameworkPackageExclusions,
            executionMode,
            frameworkSafetyConfig,
            disableTier1FrameworkSites,
            disableTier2FrameworkSites
        );
    }

    public LambdaFilterConfig withFrameworkSafetyConfig(FrameworkSafetyConfig frameworkSafetyConfig) {
        return new LambdaFilterConfig(
            hotInvocationThreshold,
            requireObservedSite,
            allowColdClassInference,
            frameworkPackageExclusions,
            executionMode,
            frameworkSafetyConfig,
            disableTier1FrameworkSites,
            disableTier2FrameworkSites
        );
    }

    public LambdaFilterConfig withDiagnosticTierDisables(
        boolean disableTier1FrameworkSites,
        boolean disableTier2FrameworkSites
    ) {
        return new LambdaFilterConfig(
            hotInvocationThreshold,
            requireObservedSite,
            allowColdClassInference,
            frameworkPackageExclusions,
            executionMode,
            frameworkSafetyConfig,
            disableTier1FrameworkSites,
            disableTier2FrameworkSites
        );
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix.replace('.', '/');
    }
}
