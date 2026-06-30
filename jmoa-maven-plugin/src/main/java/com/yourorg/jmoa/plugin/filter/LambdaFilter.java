package com.yourorg.jmoa.plugin.filter;

import com.yourorg.jmoa.plugin.JmoaExecutionMode;
import com.yourorg.jmoa.plugin.dedup.AccessPlan;
import com.yourorg.jmoa.plugin.dedup.AccessPlanner;
import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.framework.FrameworkLambdaClassifier;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyDecision;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import com.yourorg.jmoa.plugin.dedup.DeduplicationKey;
import org.objectweb.asm.Handle;

import java.util.List;
import java.util.Optional;

public final class LambdaFilter {

    private final LambdaFilterConfig config;
    private final VisibilityResolver visibilityResolver;
    private final LambdaSourceIndex sourceIndex;
    private final FrameworkLambdaClassifier frameworkClassifier;

    public LambdaFilter(LambdaFilterConfig config, ClassLoader classLoader) {
        this(config, meta -> AccessResolver.resolveVisibility(toHandle(meta), classLoader), LambdaSourceIndex.empty());
    }

    LambdaFilter(LambdaFilterConfig config, VisibilityResolver visibilityResolver) {
        this(config, visibilityResolver, LambdaSourceIndex.empty());
    }

    public LambdaFilter(LambdaFilterConfig config, ClassLoader classLoader, LambdaSourceIndex sourceIndex) {
        this(config, meta -> AccessResolver.resolveVisibility(toHandle(meta), classLoader), sourceIndex);
    }

    LambdaFilter(LambdaFilterConfig config, VisibilityResolver visibilityResolver, LambdaSourceIndex sourceIndex) {
        this.config = config;
        this.visibilityResolver = visibilityResolver;
        this.sourceIndex = sourceIndex == null ? LambdaSourceIndex.empty() : sourceIndex;
        this.frameworkClassifier = new FrameworkLambdaClassifier(config.frameworkSafetyConfig());
    }

    public LambdaFilterResult filter(List<LambdaMeta> metadata, LambdaProfileIndex profileIndex) {
        List<LambdaFilterDecision> decisions = metadata.stream()
            .map(meta -> decide(meta, profileIndex))
            .sorted((left, right) -> left.meta().siteKey().compareTo(right.meta().siteKey()))
            .toList();
        return new LambdaFilterResult(decisions);
    }

    private LambdaFilterDecision decide(LambdaMeta meta, LambdaProfileIndex profileIndex) {
        boolean observedInProfile = profileIndex.hasSite(meta.siteKey());
        boolean inferredCold = !observedInProfile
            && config.allowColdClassInference()
            && profileIndex.isColdClass(meta.ownerInternalName());
        long invocationCount = observedInProfile ? profileIndex.invocationCount(meta.siteKey()) : 0L;
        FrameworkSafetyDecision frameworkSafetyDecision = classifyFramework(meta, observedInProfile, invocationCount);

        if (meta.capturing()) {
            return LambdaFilterDecision.excluded(
                meta,
                observedInProfile,
                inferredCold,
                invocationCount,
                ExclusionReason.CAPTURING,
                AccessResolver.Visibility.UNKNOWN,
                frameworkSafetyDecision
            );
        }
        if (meta.serializable()) {
            return LambdaFilterDecision.excluded(
                meta,
                observedInProfile,
                inferredCold,
                invocationCount,
                ExclusionReason.SERIALIZABLE,
                AccessResolver.Visibility.UNKNOWN,
                frameworkSafetyDecision
            );
        }
        if (isFrameworkExcluded(meta)) {
            return LambdaFilterDecision.excluded(
                meta,
                observedInProfile,
                inferredCold,
                invocationCount,
                ExclusionReason.FRAMEWORK_EXCLUDED,
                AccessResolver.Visibility.UNKNOWN,
                frameworkSafetyDecision
            );
        }
        if (observedInProfile && invocationCount >= config.hotInvocationThreshold()) {
            return LambdaFilterDecision.excluded(
                meta,
                observedInProfile,
                inferredCold,
                invocationCount,
                ExclusionReason.HOT_SITE,
                AccessResolver.Visibility.UNKNOWN,
                frameworkSafetyDecision
            );
        }
        if (config.requireObservedSite() && !observedInProfile && !inferredCold) {
            return LambdaFilterDecision.excluded(
                meta,
                false,
                false,
                0L,
                ExclusionReason.NOT_OBSERVED,
                AccessResolver.Visibility.UNKNOWN,
                frameworkSafetyDecision
            );
        }
        if (config.executionMode() == JmoaExecutionMode.MODE_C && !frameworkSafetyDecision.allowed()) {
            return LambdaFilterDecision.excluded(
                meta,
                observedInProfile,
                inferredCold,
                invocationCount,
                ExclusionReason.FRAMEWORK_SAFETY_DENIED,
                AccessResolver.Visibility.UNKNOWN,
                frameworkSafetyDecision
            );
        }

        AccessResolver.Visibility visibility = visibilityResolver.resolve(meta);
        if (visibility == AccessResolver.Visibility.UNKNOWN) {
            return LambdaFilterDecision.excluded(
                meta,
                observedInProfile,
                inferredCold,
                invocationCount,
                ExclusionReason.ACCESS_UNKNOWN,
                visibility,
                frameworkSafetyDecision
            );
        }

        Optional<AccessPlan> accessPlan = AccessPlanner.plan(
            new DeduplicationKey(
                meta.samInterfaceInternalName(),
                meta.samMethodTypeDesc(),
                meta.implTag(),
                meta.implOwner(),
                meta.implName(),
                meta.implDesc(),
                meta.instantiatedMethodTypeDesc()
            ),
            visibility,
            true
        );
        if (accessPlan.isEmpty()) {
            return LambdaFilterDecision.excluded(
                meta,
                observedInProfile,
                inferredCold,
                invocationCount,
                ExclusionReason.ACCESS_DENIED,
                visibility,
                frameworkSafetyDecision
            );
        }

        LambdaTier tier = accessPlan.get().tier() == com.yourorg.jmoa.plugin.dedup.AccessTier.TIER1_PUBLIC_LOOKUP
            ? LambdaTier.TIER1
            : LambdaTier.TIER2;
        if (config.executionMode() == JmoaExecutionMode.MODE_C
            && frameworkSafetyDecision.allowed()
            && frameworkSafetyDecision.level() != com.yourorg.jmoa.plugin.framework.FrameworkSafetyLevel.APPLICATION
            && ((tier == LambdaTier.TIER1 && config.disableTier1FrameworkSites())
                || (tier == LambdaTier.TIER2 && config.disableTier2FrameworkSites()))) {
            return LambdaFilterDecision.excluded(
                meta,
                observedInProfile,
                inferredCold,
                invocationCount,
                ExclusionReason.FRAMEWORK_TIER_DIAGNOSTIC_DISABLED,
                visibility,
                frameworkSafetyDecision
            );
        }
        return LambdaFilterDecision.eligible(meta, observedInProfile, inferredCold, invocationCount, tier, visibility, frameworkSafetyDecision);
    }

    private boolean isFrameworkExcluded(LambdaMeta meta) {
        String owner = meta.ownerInternalName();
        for (String prefix : config.frameworkPackageExclusions()) {
            if (!prefix.isBlank() && owner.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private FrameworkSafetyDecision classifyFramework(
        LambdaMeta meta,
        boolean observedInProfile,
        long invocationCount
    ) {
        return frameworkClassifier.classify(
            meta,
            sourceIndex.rootKindFor(meta.ownerInternalName()),
            invocationCount,
            observedInProfile
        );
    }

    private static Handle toHandle(LambdaMeta meta) {
        return new Handle(meta.implTag(), meta.implOwner(), meta.implName(), meta.implDesc(), meta.implTag() == org.objectweb.asm.Opcodes.H_INVOKEINTERFACE);
    }

    @FunctionalInterface
    interface VisibilityResolver {
        AccessResolver.Visibility resolve(LambdaMeta meta);
    }
}
