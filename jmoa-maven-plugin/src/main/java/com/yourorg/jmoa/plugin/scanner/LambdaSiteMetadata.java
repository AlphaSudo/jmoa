package com.yourorg.jmoa.plugin.scanner;

import com.yourorg.jmoa.plugin.model.LambdaMeta;

final class LambdaSiteMetadata {

    private LambdaSiteMetadata() {
    }

    static LambdaMeta toMeta(LambdaSite site) {
        return new LambdaMeta(
            site.siteKey(),
            site.classNode().name,
            site.packageInternalName(),
            site.methodNode().name,
            site.methodNode().desc,
            site.siteOrdinalInMethod(),
            site.indyName(),
            site.indyDesc(),
            site.samMethodType().getDescriptor(),
            site.isCapturing(),
            site.isSerializable(),
            site.implHandle().getTag(),
            site.implHandle().getOwner(),
            site.implHandle().isInterface(),
            site.implHandle().getName(),
            site.implHandle().getDesc(),
            site.instantiatedMethodType().getDescriptor(),
            0L
        );
    }
}
