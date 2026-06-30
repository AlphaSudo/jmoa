package com.yourorg.jmoa.plugin.weave;

public record LambdaWeaveTarget(
    String siteKey,
    WeaveKind kind,
    String samInterfaceInternalName,
    int slotId,
    String factoryMethodName,
    String factoryMethodDescriptor,
    String tier2AdapterInternalName,
    String tier2AdapterFieldName,
    String implOwner,
    String implName,
    String implDesc,
    boolean requiresAccessWidening
) {

    public enum WeaveKind {
        TIER1_FACTORY,
        TIER2_ADAPTER
    }

    public static LambdaWeaveTarget tier1(
        String siteKey,
        String samInterfaceInternalName,
        int slotId,
        String factoryMethodName,
        String factoryMethodDescriptor
    ) {
        return new LambdaWeaveTarget(
            siteKey,
            WeaveKind.TIER1_FACTORY,
            samInterfaceInternalName,
            slotId,
            factoryMethodName,
            factoryMethodDescriptor,
            null,
            null,
            null,
            null,
            null,
            false
        );
    }

    public static LambdaWeaveTarget tier2(
        String siteKey,
        String samInterfaceInternalName,
        String tier2AdapterInternalName,
        String tier2AdapterFieldName,
        String implOwner,
        String implName,
        String implDesc,
        boolean requiresAccessWidening
    ) {
        return new LambdaWeaveTarget(
            siteKey,
            WeaveKind.TIER2_ADAPTER,
            samInterfaceInternalName,
            -1,
            null,
            null,
            tier2AdapterInternalName,
            tier2AdapterFieldName,
            implOwner,
            implName,
            implDesc,
            requiresAccessWidening
        );
    }
}
