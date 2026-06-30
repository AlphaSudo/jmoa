package com.yourorg.jmoa.plugin.model;

import org.objectweb.asm.Type;

public record LambdaMeta(
    String siteKey,
    String ownerInternalName,
    String packageInternalName,
    String enclosingMethodName,
    String enclosingMethodDesc,
    int siteOrdinalInMethod,
    String indyName,
    String indyFactoryDesc,
    String samMethodTypeDesc,
    boolean capturing,
    boolean serializable,
    int implTag,
    String implOwner,
    boolean implOwnerInterface,
    String implName,
    String implDesc,
    String instantiatedMethodTypeDesc,
    long invocationCount
) {

    public LambdaMeta(
        String siteKey,
        String ownerInternalName,
        String packageInternalName,
        String enclosingMethodName,
        String enclosingMethodDesc,
        int siteOrdinalInMethod,
        String indyName,
        String indyFactoryDesc,
        String samMethodTypeDesc,
        boolean capturing,
        boolean serializable,
        int implTag,
        String implOwner,
        String implName,
        String implDesc,
        String instantiatedMethodTypeDesc,
        long invocationCount
    ) {
        this(
            siteKey,
            ownerInternalName,
            packageInternalName,
            enclosingMethodName,
            enclosingMethodDesc,
            siteOrdinalInMethod,
            indyName,
            indyFactoryDesc,
            samMethodTypeDesc,
            capturing,
            serializable,
            implTag,
            implOwner,
            false,
            implName,
            implDesc,
            instantiatedMethodTypeDesc,
            invocationCount
        );
    }

    public static String buildSiteKey(
        String ownerInternalName,
        String enclosingMethodName,
        String enclosingMethodDesc,
        int siteOrdinalInMethod,
        String indyName,
        String indyFactoryDesc,
        int implTag,
        String implOwner,
        String implName,
        String implDesc
    ) {
        return ownerInternalName
            + "::"
            + enclosingMethodName
            + enclosingMethodDesc
            + "#"
            + siteOrdinalInMethod
            + "|"
            + indyName
            + "|"
            + indyFactoryDesc
            + "|"
            + implTag
            + "|"
            + implOwner
            + "::"
            + implName
            + implDesc;
    }

    public boolean isStatelessCandidate() {
        return !capturing && !serializable;
    }

    public String samInterfaceInternalName() {
        return Type.getReturnType(indyFactoryDesc).getInternalName();
    }

    public String duplicateGroupKey() {
        return samInterfaceInternalName()
            + "|"
            + samMethodTypeDesc
            + "|"
            + implTag
            + "|"
            + implOwner
            + "|"
            + implName
            + "|"
            + implDesc
            + "|"
            + instantiatedMethodTypeDesc;
    }
}
