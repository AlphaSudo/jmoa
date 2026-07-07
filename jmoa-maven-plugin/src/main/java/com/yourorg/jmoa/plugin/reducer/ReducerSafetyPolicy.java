package com.yourorg.jmoa.plugin.reducer;

import java.util.List;
import java.util.Map;

public final class ReducerSafetyPolicy {

    public void validate(ReducerConfig config) {
        if (config.inputDir() == null || !config.inputDir().isDirectory()) {
            throw new IllegalArgumentException("jmoa.reducer.inputDir must point to a directory containing dependency jars.");
        }
        config.parsedEngine();
        if (config.stripLineNumberTable()) {
            unsafe("LineNumberTable", ReducerSafetyCategory.UNSAFE_DIAGNOSTIC_CRITICAL);
        }
        if (config.stripSourceFile()) {
            unsafe("SourceFile", ReducerSafetyCategory.UNSAFE_DIAGNOSTIC_CRITICAL);
        }
        if (config.stripStackMapTable()) {
            unsafe("StackMapTable", ReducerSafetyCategory.UNSAFE_VERIFICATION);
        }
        if (config.stripAnnotations()) {
            unsafe("annotations", ReducerSafetyCategory.UNSAFE_FRAMEWORK_SEMANTIC);
        }
        if (config.stripSignature()) {
            unsafe("Signature", ReducerSafetyCategory.UNSAFE_FRAMEWORK_SEMANTIC);
        }
        if (config.stripBootstrapMethods()) {
            unsafe("BootstrapMethods", ReducerSafetyCategory.UNSAFE_FRAMEWORK_SEMANTIC);
        }
        if (config.optimize() && config.reportOnly()) {
            throw new IllegalArgumentException("UNSAFE_REDUCER_NOT_IMPLEMENTED: set jmoa.reducer.reportOnly=false before optimization.");
        }
        if (!config.optimize() && !config.reportOnly()) {
            throw new IllegalArgumentException("Reducer mutation requires jmoa.reducer.optimize=true.");
        }
        if (config.optimize() && config.parsedProfile() != ReducerProfile.RELEASE_LOW_FOOTPRINT) {
            throw new IllegalArgumentException("Reducer mutation requires jmoa.reducer.profile=release-low-footprint.");
        }
        if (config.optimize() && (!config.stripLocalVariableTable() || !config.stripLocalVariableTypeTable())) {
            throw new IllegalArgumentException("Reducer mutation requires both stripLocalVariableTable and stripLocalVariableTypeTable.");
        }
    }

    public ReducerSafetyTaxonomy taxonomy() {
        return new ReducerSafetyTaxonomy(
            "v2-e1-bytecode-reducer-safety-taxonomy",
            List.of(
                new ReducerSafetyEntry("LocalVariableTable", ReducerSafetyCategory.SAFE_OPT_IN_RELEASE,
                    "Allowed only for explicit release-low-footprint artifacts."),
                new ReducerSafetyEntry("LocalVariableTypeTable", ReducerSafetyCategory.SAFE_OPT_IN_RELEASE,
                    "Allowed only for explicit release-low-footprint artifacts."),
                new ReducerSafetyEntry("LineNumberTable", ReducerSafetyCategory.UNSAFE_DIAGNOSTIC_CRITICAL,
                    "Preserved to keep stack traces and diagnostics useful."),
                new ReducerSafetyEntry("SourceFile", ReducerSafetyCategory.UNSAFE_DIAGNOSTIC_CRITICAL,
                    "Preserved for diagnostics and source context."),
                new ReducerSafetyEntry("StackMapTable", ReducerSafetyCategory.UNSAFE_VERIFICATION,
                    "Preserved because modern class verification depends on stack-map frames."),
                new ReducerSafetyEntry("RuntimeVisibleAnnotations", ReducerSafetyCategory.UNSAFE_FRAMEWORK_SEMANTIC,
                    "Preserved because frameworks and reflection can inspect annotations."),
                new ReducerSafetyEntry("RuntimeInvisibleAnnotations", ReducerSafetyCategory.UNSAFE_FRAMEWORK_SEMANTIC,
                    "Preserved because tooling and frameworks can depend on annotation metadata."),
                new ReducerSafetyEntry("Signature", ReducerSafetyCategory.UNSAFE_FRAMEWORK_SEMANTIC,
                    "Preserved because generic signatures can be inspected by frameworks."),
                new ReducerSafetyEntry("BootstrapMethods", ReducerSafetyCategory.UNSAFE_FRAMEWORK_SEMANTIC,
                    "Preserved because invokedynamic bootstrap metadata is executable semantics; the asm engine skips these classes, while the opt-in raw engine preserves the attribute byte-for-byte and removes only nested Code local-variable tables."),
                new ReducerSafetyEntry("InnerClasses/Nest/Record/PermittedSubclasses", ReducerSafetyCategory.UNSAFE_FRAMEWORK_SEMANTIC,
                    "Preserved because nesting, records, sealed classes, and proxies can depend on metadata.")
            ),
            Map.of(
                "default", "disabled and report-only",
                "firstMutation", "LocalVariableTable and LocalVariableTypeTable only",
                "bootstrapMethodsPolicy", "asm engine skips class in mutation mode; raw engine may reduce only by preserving BootstrapMethods",
                "mutationProfile", "release-low-footprint"
            )
        );
    }

    private static void unsafe(String attribute, ReducerSafetyCategory category) {
        throw new IllegalArgumentException("UNSAFE_REDUCER_NOT_IMPLEMENTED: " + attribute + " is " + category);
    }
}
