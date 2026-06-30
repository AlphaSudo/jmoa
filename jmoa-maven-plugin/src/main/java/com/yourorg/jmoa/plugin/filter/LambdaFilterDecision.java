package com.yourorg.jmoa.plugin.filter;

import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyDecision;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyLevel;
import com.yourorg.jmoa.plugin.model.LambdaMeta;

import java.util.List;

public record LambdaFilterDecision(
    LambdaMeta meta,
    boolean observedInProfile,
    boolean inferredCold,
    long invocationCount,
    boolean eligible,
    LambdaTier tier,
    ExclusionReason exclusionReason,
    AccessResolver.Visibility accessVisibility,
    FrameworkSafetyDecision frameworkSafetyDecision
) {

    public static LambdaFilterDecision eligible(
        LambdaMeta meta,
        boolean observedInProfile,
        boolean inferredCold,
        long invocationCount,
        LambdaTier tier,
        AccessResolver.Visibility accessVisibility
    ) {
        return new LambdaFilterDecision(
            meta,
            observedInProfile,
            inferredCold,
            invocationCount,
            true,
            tier,
            null,
            accessVisibility,
            FrameworkSafetyDecision.application(meta.siteKey(), meta.ownerInternalName())
        );
    }

    public static LambdaFilterDecision eligible(
        LambdaMeta meta,
        boolean observedInProfile,
        boolean inferredCold,
        long invocationCount,
        LambdaTier tier,
        AccessResolver.Visibility accessVisibility,
        FrameworkSafetyDecision frameworkSafetyDecision
    ) {
        return new LambdaFilterDecision(
            meta,
            observedInProfile,
            inferredCold,
            invocationCount,
            true,
            tier,
            null,
            accessVisibility,
            frameworkSafetyDecision
        );
    }

    public static LambdaFilterDecision excluded(
        LambdaMeta meta,
        boolean observedInProfile,
        boolean inferredCold,
        long invocationCount,
        ExclusionReason exclusionReason,
        AccessResolver.Visibility accessVisibility
    ) {
        return new LambdaFilterDecision(
            meta,
            observedInProfile,
            inferredCold,
            invocationCount,
            false,
            null,
            exclusionReason,
            accessVisibility,
            FrameworkSafetyDecision.application(meta.siteKey(), meta.ownerInternalName())
        );
    }

    public static LambdaFilterDecision excluded(
        LambdaMeta meta,
        boolean observedInProfile,
        boolean inferredCold,
        long invocationCount,
        ExclusionReason exclusionReason,
        AccessResolver.Visibility accessVisibility,
        FrameworkSafetyDecision frameworkSafetyDecision
    ) {
        return new LambdaFilterDecision(
            meta,
            observedInProfile,
            inferredCold,
            invocationCount,
            false,
            null,
            exclusionReason,
            accessVisibility,
            frameworkSafetyDecision
        );
    }

    public FrameworkSafetyLevel frameworkSafetyLevel() {
        return frameworkSafetyDecision == null ? FrameworkSafetyLevel.APPLICATION : frameworkSafetyDecision.level();
    }

    public List<String> frameworkSafetyReasons() {
        if (frameworkSafetyDecision == null) {
            return List.of();
        }
        return frameworkSafetyDecision.reasons().stream().map(Enum::name).toList();
    }
}
