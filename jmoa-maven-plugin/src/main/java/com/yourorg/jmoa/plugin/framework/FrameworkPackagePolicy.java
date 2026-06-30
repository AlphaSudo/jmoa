package com.yourorg.jmoa.plugin.framework;

import com.yourorg.jmoa.plugin.ClassRootKind;

import java.util.ArrayList;
import java.util.List;

public final class FrameworkPackagePolicy {

    private final FrameworkSafetyConfig config;

    public FrameworkPackagePolicy(FrameworkSafetyConfig config) {
        this.config = config;
    }

    public PackageDecision classifyOwner(String ownerInternalName, ClassRootKind rootKind) {
        List<FrameworkSafetyReason> reasons = new ArrayList<>();

        if (rootKind == ClassRootKind.PROJECT_OUTPUT) {
            reasons.add(FrameworkSafetyReason.PROJECT_OUTPUT_CLASS);
            return new PackageDecision(FrameworkSafetyLevel.APPLICATION, true, reasons);
        }

        if (rootKind == ClassRootKind.ADDITIONAL_DIRECTORY) {
            if (looksLikeSpringAot(ownerInternalName)) {
                if (config.allowSpringAotFrameworkSites()) {
                    reasons.add(FrameworkSafetyReason.SAFE_SPRING_AOT_CLASS);
                    return new PackageDecision(FrameworkSafetyLevel.FRAMEWORK_SAFE, true, reasons);
                }
                reasons.add(FrameworkSafetyReason.SPRING_AOT_DISABLED);
                return new PackageDecision(FrameworkSafetyLevel.FRAMEWORK_UNSAFE, false, reasons);
            }
            reasons.add(FrameworkSafetyReason.SAFE_ADDITIONAL_DIRECTORY);
            return new PackageDecision(FrameworkSafetyLevel.FRAMEWORK_SAFE, true, reasons);
        }

        for (String denyPrefix : config.denyPrefixes()) {
            if (ownerInternalName.startsWith(denyPrefix)) {
                reasons.add(reasonForDeniedPrefix(denyPrefix));
                return new PackageDecision(FrameworkSafetyLevel.FRAMEWORK_UNSAFE, false, reasons);
            }
        }

        for (String allowPrefix : config.allowPrefixes()) {
            if (ownerInternalName.startsWith(allowPrefix)) {
                if (!config.allowExpandedDependencySites()) {
                    reasons.add(FrameworkSafetyReason.EXPANDED_DEPENDENCY_DISABLED);
                    return new PackageDecision(FrameworkSafetyLevel.FRAMEWORK_UNSAFE, false, reasons);
                }
                reasons.add(FrameworkSafetyReason.SAFE_EXPANDED_DEPENDENCY_PREFIX);
                return new PackageDecision(FrameworkSafetyLevel.FRAMEWORK_SAFE, true, reasons);
            }
        }

        reasons.add(FrameworkSafetyReason.UNKNOWN_FRAMEWORK_PACKAGE);
        return new PackageDecision(
            FrameworkSafetyLevel.FRAMEWORK_UNKNOWN,
            config.allowUnknownFrameworkSites(),
            reasons
        );
    }

    private boolean looksLikeSpringAot(String ownerInternalName) {
        return ownerInternalName.contains("__BeanDefinitions")
            || ownerInternalName.contains("__Autowiring")
            || ownerInternalName.contains("__ApplicationContextInitializer")
            || ownerInternalName.startsWith("org/springframework/aot/")
            || ownerInternalName.contains("/aot/");
    }

    private FrameworkSafetyReason reasonForDeniedPrefix(String prefix) {
        if (prefix.startsWith("com/fasterxml/jackson")) {
            return FrameworkSafetyReason.JACKSON_INTERNAL_PACKAGE;
        }
        if (prefix.startsWith("org/hibernate")) {
            return FrameworkSafetyReason.HIBERNATE_INTERNAL_PACKAGE;
        }
        if (prefix.contains("cglib") || prefix.contains("bytebuddy") || prefix.contains("javassist") || prefix.contains("proxy")) {
            return FrameworkSafetyReason.PROXY_OR_ENHANCER_PACKAGE;
        }
        return FrameworkSafetyReason.SPRING_REFLECTION_PACKAGE;
    }

    public record PackageDecision(
        FrameworkSafetyLevel level,
        boolean allowed,
        List<FrameworkSafetyReason> reasons
    ) {
        public PackageDecision {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }
}
