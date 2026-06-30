package com.yourorg.jmoa.plugin.framework;

import com.yourorg.jmoa.plugin.ClassRootKind;
import com.yourorg.jmoa.plugin.model.LambdaMeta;

import java.util.ArrayList;
import java.util.List;

public final class FrameworkLambdaClassifier {

    private final FrameworkSafetyConfig config;
    private final FrameworkPackagePolicy packagePolicy;

    public FrameworkLambdaClassifier(FrameworkSafetyConfig config) {
        this.config = config;
        this.packagePolicy = new FrameworkPackagePolicy(config);
    }

    public FrameworkSafetyDecision classify(
        LambdaMeta meta,
        ClassRootKind rootKind,
        long invocationCount,
        boolean observedInProfile
    ) {
        if (!config.enabled()) {
            return FrameworkSafetyDecision.application(meta.siteKey(), meta.ownerInternalName());
        }

        boolean siteKeyAdmitted = config.isSiteKeyAdmitted(meta.siteKey());

        List<FrameworkSafetyReason> reasons = new ArrayList<>();
        FrameworkPackagePolicy.PackageDecision packageDecision =
            packagePolicy.classifyOwner(meta.ownerInternalName(), rootKind);
        reasons.addAll(packageDecision.reasons());

        if (!packageDecision.allowed() && !siteKeyAdmitted) {
            return FrameworkSafetyDecision.deny(meta.siteKey(), meta.ownerInternalName(), rootKind, packageDecision.level(), reasons);
        }
        if (siteKeyAdmitted && !packageDecision.allowed()) {
            reasons.add(FrameworkSafetyReason.OBSERVED_SITE_KEY_ADMITTED);
        }

        // Hard safety gates — always enforced even for siteKey admissions
        if (meta.capturing()) {
            reasons.add(FrameworkSafetyReason.CAPTURING_LAMBDA);
            return FrameworkSafetyDecision.deny(meta.siteKey(), meta.ownerInternalName(), rootKind, packageDecision.level(), reasons);
        }
        if (meta.serializable()) {
            reasons.add(FrameworkSafetyReason.SERIALIZABLE_LAMBDA);
            return FrameworkSafetyDecision.deny(meta.siteKey(), meta.ownerInternalName(), rootKind, packageDecision.level(), reasons);
        }

        if (packageDecision.level() != FrameworkSafetyLevel.APPLICATION && !observedInProfile && !siteKeyAdmitted) {
            reasons.add(FrameworkSafetyReason.MISSING_PROFILE);
            return FrameworkSafetyDecision.deny(meta.siteKey(), meta.ownerInternalName(), rootKind, packageDecision.level(), reasons);
        }
        if (invocationCount >= config.frameworkHotThreshold()) {
            reasons.add(FrameworkSafetyReason.HOT_SITE);
            return FrameworkSafetyDecision.deny(meta.siteKey(), meta.ownerInternalName(), rootKind, packageDecision.level(), reasons);
        }
        if (!config.safeSamInterfaces().contains(meta.samInterfaceInternalName())) {
            reasons.add(FrameworkSafetyReason.UNSUPPORTED_SAM_KIND);
            return FrameworkSafetyDecision.deny(meta.siteKey(), meta.ownerInternalName(), rootKind, packageDecision.level(), reasons);
        }

        reasons.add(FrameworkSafetyReason.SAFE_SAM_KIND);
        if (siteKeyAdmitted) {
            reasons.add(FrameworkSafetyReason.OBSERVED_SITE_KEY_ADMITTED);
        }
        if (observedInProfile) {
            reasons.add(FrameworkSafetyReason.COLD_PROFILED_SITE);
        }
        return FrameworkSafetyDecision.allow(meta.siteKey(), meta.ownerInternalName(), rootKind, packageDecision.level(), reasons);
    }
}
