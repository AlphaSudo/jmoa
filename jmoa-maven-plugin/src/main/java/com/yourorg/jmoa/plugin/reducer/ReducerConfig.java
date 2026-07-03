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
    boolean stripBootstrapMethods
) {

    public ReducerProfile parsedProfile() {
        return ReducerProfile.parse(profile);
    }

    public boolean mutationEnabled() {
        return optimize
            && !reportOnly
            && parsedProfile() == ReducerProfile.RELEASE_LOW_FOOTPRINT
            && stripLocalVariableTable
            && stripLocalVariableTypeTable;
    }
}
