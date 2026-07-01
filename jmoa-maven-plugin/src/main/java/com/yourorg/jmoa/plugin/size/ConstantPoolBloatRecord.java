package com.yourorg.jmoa.plugin.size;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;

public record ConstantPoolBloatRecord(
    String className,
    String artifact,
    int constantPoolCount,
    int utf8EntryCount,
    int classRefCount,
    int methodRefCount,
    int interfaceMethodRefCount,
    int fieldRefCount,
    int nameAndTypeCount,
    int methodHandleCount,
    int methodTypeCount,
    int dynamicConstantCount,
    int invokeDynamicConstantCount,
    int stringConstantCount,
    int bootstrapMethodsCount,
    int bootstrapMethodArgCount,
    int duplicateUtf8Count,
    int duplicateDescriptorCount,
    GeneratedClassFamily generatedFamily
) {
}
