package com.yourorg.jmoa.plugin.reducer;

import java.io.File;

public record ReducerConfig(
    boolean reportOnly,
    boolean optimize,
    String profile,
    File inputDir,
    File outputDir,
    boolean stripLocalVariableTable,
    boolean stripLocalVariableTypeTable,
    boolean stripLineNumberTable,
    boolean stripSourceFile,
    boolean stripStackMapTable,
    boolean stripAnnotations,
    boolean stripSignature,
    boolean stripBootstrapMethods,
    String engine,
    boolean includeApplicationClasses,
    File applicationInputDir,
    String generatedFamilies,
    String artifactIncludes,
    String artifactExcludes,
    String operation,
    String jarLayout
) {

    public ReducerConfig(
        boolean reportOnly,
        boolean optimize,
        String profile,
        File inputDir,
        File outputDir,
        boolean stripLocalVariableTable,
        boolean stripLocalVariableTypeTable,
        boolean stripLineNumberTable,
        boolean stripSourceFile,
        boolean stripStackMapTable,
        boolean stripAnnotations,
        boolean stripSignature,
        boolean stripBootstrapMethods
    ) {
        this(
            reportOnly,
            optimize,
            profile,
            inputDir,
            outputDir,
            stripLocalVariableTable,
            stripLocalVariableTypeTable,
            stripLineNumberTable,
            stripSourceFile,
            stripStackMapTable,
            stripAnnotations,
            stripSignature,
            stripBootstrapMethods,
            ReducerEngine.ASM.propertyValue(),
            false,
            null,
            "report-only",
            "",
            "",
            ReducerOperation.STRIP.propertyValue(),
            JarLayoutMode.CURRENT.propertyValue()
        );
    }

    public ReducerConfig(
        boolean reportOnly,
        boolean optimize,
        String profile,
        File inputDir,
        File outputDir,
        boolean stripLocalVariableTable,
        boolean stripLocalVariableTypeTable,
        boolean stripLineNumberTable,
        boolean stripSourceFile,
        boolean stripStackMapTable,
        boolean stripAnnotations,
        boolean stripSignature,
        boolean stripBootstrapMethods,
        String engine
    ) {
        this(
            reportOnly,
            optimize,
            profile,
            inputDir,
            outputDir,
            stripLocalVariableTable,
            stripLocalVariableTypeTable,
            stripLineNumberTable,
            stripSourceFile,
            stripStackMapTable,
            stripAnnotations,
            stripSignature,
            stripBootstrapMethods,
            engine,
            false,
            null,
            "report-only",
            "",
            "",
            ReducerOperation.STRIP.propertyValue(),
            JarLayoutMode.CURRENT.propertyValue()
        );
    }

    public ReducerConfig(
        boolean reportOnly,
        boolean optimize,
        String profile,
        File inputDir,
        File outputDir,
        boolean stripLocalVariableTable,
        boolean stripLocalVariableTypeTable,
        boolean stripLineNumberTable,
        boolean stripSourceFile,
        boolean stripStackMapTable,
        boolean stripAnnotations,
        boolean stripSignature,
        boolean stripBootstrapMethods,
        String engine,
        boolean includeApplicationClasses,
        File applicationInputDir,
        String generatedFamilies
    ) {
        this(
            reportOnly, optimize, profile, inputDir, outputDir,
            stripLocalVariableTable, stripLocalVariableTypeTable,
            stripLineNumberTable, stripSourceFile, stripStackMapTable,
            stripAnnotations, stripSignature, stripBootstrapMethods,
            engine, includeApplicationClasses, applicationInputDir, generatedFamilies,
            "", "", ReducerOperation.STRIP.propertyValue(), JarLayoutMode.CURRENT.propertyValue()
        );
    }

    public ReducerConfig(
        boolean reportOnly,
        boolean optimize,
        String profile,
        File inputDir,
        File outputDir,
        boolean stripLocalVariableTable,
        boolean stripLocalVariableTypeTable,
        boolean stripLineNumberTable,
        boolean stripSourceFile,
        boolean stripStackMapTable,
        boolean stripAnnotations,
        boolean stripSignature,
        boolean stripBootstrapMethods,
        String engine,
        boolean includeApplicationClasses,
        File applicationInputDir,
        String generatedFamilies,
        String artifactIncludes,
        String artifactExcludes
    ) {
        this(
            reportOnly, optimize, profile, inputDir, outputDir,
            stripLocalVariableTable, stripLocalVariableTypeTable,
            stripLineNumberTable, stripSourceFile, stripStackMapTable,
            stripAnnotations, stripSignature, stripBootstrapMethods,
            engine, includeApplicationClasses, applicationInputDir, generatedFamilies,
            artifactIncludes, artifactExcludes,
            ReducerOperation.STRIP.propertyValue(), JarLayoutMode.CURRENT.propertyValue()
        );
    }

    public ReducerProfile parsedProfile() {
        return ReducerProfile.parse(profile);
    }

    public ReducerEngine parsedEngine() {
        return ReducerEngine.parse(engine);
    }

    public ReducerOperation parsedOperation() {
        return ReducerOperation.parse(operation);
    }

    public JarLayoutMode parsedJarLayout() {
        return JarLayoutMode.parse(jarLayout);
    }

    public boolean mutationEnabled() {
        return optimize
            && !reportOnly
            && parsedProfile() == ReducerProfile.RELEASE_LOW_FOOTPRINT
            && (parsedOperation() == ReducerOperation.REPACK_ONLY
                || (stripLocalVariableTable && stripLocalVariableTypeTable));
    }
}
