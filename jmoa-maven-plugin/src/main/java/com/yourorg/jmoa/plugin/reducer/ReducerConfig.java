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
    String engine
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
            ReducerEngine.ASM.propertyValue()
        );
    }

    public ReducerProfile parsedProfile() {
        return ReducerProfile.parse(profile);
    }

    public ReducerEngine parsedEngine() {
        return ReducerEngine.parse(engine);
    }

    public boolean mutationEnabled() {
        return optimize
            && !reportOnly
            && parsedProfile() == ReducerProfile.RELEASE_LOW_FOOTPRINT
            && stripLocalVariableTable
            && stripLocalVariableTypeTable;
    }
}
