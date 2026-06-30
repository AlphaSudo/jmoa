package com.yourorg.jmoa.plugin.dedup;

public record DeduplicationKey(
    String samInterfaceInternalName,
    String samMethodDescriptor,
    int implTag,
    String implOwner,
    String implName,
    String implDesc,
    String instantiatedMethodType
) {}
