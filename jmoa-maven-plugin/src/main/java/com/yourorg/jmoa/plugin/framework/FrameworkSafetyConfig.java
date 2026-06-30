package com.yourorg.jmoa.plugin.framework;

import java.util.List;
import java.util.Set;

public record FrameworkSafetyConfig(
    boolean enabled,
    boolean allowSpringAotFrameworkSites,
    boolean allowExpandedDependencySites,
    boolean allowUnknownFrameworkSites,
    List<String> allowPrefixes,
    List<String> denyPrefixes,
    long frameworkHotThreshold,
    List<String> safeSamInterfaces,
    Set<String> observedSiteKeyAdmissions
) {

    public FrameworkSafetyConfig {
        allowPrefixes = normalize(allowPrefixes);
        denyPrefixes = normalize(denyPrefixes);
        safeSamInterfaces = safeSamInterfaces == null ? List.of() : List.copyOf(safeSamInterfaces);
        observedSiteKeyAdmissions = observedSiteKeyAdmissions == null ? Set.of() : Set.copyOf(observedSiteKeyAdmissions);
    }

    public static FrameworkSafetyConfig defaults() {
        return new FrameworkSafetyConfig(
            false,
            true,
            true,
            false,
            List.of(
                "org.springframework.context",
                "org.springframework.boot",
                "org.springframework.core",
                "org.springframework.data",
                "org.springframework.beans.factory.aot",
                "org.springframework.beans.factory.support"
            ),
            List.of(
                "org.springframework.beans",
                "org.springframework.expression",
                "org.springframework.cglib",
                "org.springframework.aop.framework",
                "org.hibernate",
                "com.fasterxml.jackson",
                "net.bytebuddy",
                "javassist"
            ),
            10_000,
            List.of(
                "java/util/function/Function",
                "java/util/function/Predicate",
                "java/util/function/Supplier",
                "java/util/function/Consumer",
                "java/util/function/BiConsumer"
            ),
            Set.of()
        );
    }

    public boolean isSiteKeyAdmitted(String siteKey) {
        return observedSiteKeyAdmissions != null && observedSiteKeyAdmissions.contains(siteKey);
    }

    private static List<String> normalize(List<String> prefixes) {
        if (prefixes == null) {
            return List.of();
        }
        return prefixes.stream()
            .map(prefix -> prefix == null ? "" : prefix.trim().replace('.', '/'))
            .filter(prefix -> !prefix.isBlank())
            .distinct()
            .toList();
    }
}
