package com.yourorg.jmoa.plugin.size;

import java.util.List;

public record ClassfileSizeProfile(
    String metadataVersion,
    String generatedAt,
    int totalClassesScanned,
    long totalClassfileBytes,
    long totalMethodCodeBytes,
    int largestMethodCodeLength,
    List<ClassfileSizeFamilySummary> familyBreakdown,
    List<ClassfileSizeRecord> classes,
    List<MethodSizeRecord> methods,
    List<ConstantPoolBloatRecord> constantPools,
    List<AttributeFootprintRecord> attributes
) {

    public ClassfileSizeProfile {
        familyBreakdown = familyBreakdown == null ? List.of() : List.copyOf(familyBreakdown);
        classes = classes == null ? List.of() : List.copyOf(classes);
        methods = methods == null ? List.of() : List.copyOf(methods);
        constantPools = constantPools == null ? List.of() : List.copyOf(constantPools);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }
}
