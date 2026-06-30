package com.yourorg.jmoa.plugin;

import java.io.File;

public record ClassRootDescriptor(
    File rootDirectory,
    boolean projectOwned,
    ClassRootKind kind
) {

    public ClassRootDescriptor(File rootDirectory, boolean projectOwned) {
        this(rootDirectory, projectOwned, projectOwned ? ClassRootKind.PROJECT_OUTPUT : ClassRootKind.ADDITIONAL_DIRECTORY);
    }
}
