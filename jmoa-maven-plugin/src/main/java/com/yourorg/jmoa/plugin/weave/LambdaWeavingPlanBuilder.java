package com.yourorg.jmoa.plugin.weave;

import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;
import com.yourorg.jmoa.plugin.runtime.Tier1RuntimePlan;
import com.yourorg.jmoa.plugin.runtime.Tier1RuntimePlanResult;
import com.yourorg.jmoa.plugin.runtime.Tier1DirectAdapterNamingStrategy;
import com.yourorg.jmoa.plugin.runtime.Tier2AdapterNamingStrategy;
import org.objectweb.asm.Opcodes;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LambdaWeavingPlanBuilder {

    private final boolean consolidateTier2ByPackageSam;
    private final boolean consolidateTier2ByPackage;
    private final boolean consolidateTier2ByPackageSignature;
    private final boolean rootPackageSignature;
    private final java.util.Map<String, Integer> rootOrdinalBySiteKey;
    private final boolean compact;
    private final boolean directTier1;

    public LambdaWeavingPlanBuilder() {
        this(false, false, false, false, false);
    }

    public LambdaWeavingPlanBuilder(boolean consolidateTier2ByPackageSam) {
        this(consolidateTier2ByPackageSam, false, false, false, false);
    }

    public LambdaWeavingPlanBuilder(boolean consolidateTier2ByPackageSam, boolean consolidateTier2ByPackage) {
        this(consolidateTier2ByPackageSam, consolidateTier2ByPackage, false, false, false);
    }

    public LambdaWeavingPlanBuilder(boolean consolidateTier2ByPackageSam, boolean consolidateTier2ByPackage,
                                    boolean consolidateTier2ByPackageSignature, boolean compact) {
        this(consolidateTier2ByPackageSam, consolidateTier2ByPackage, consolidateTier2ByPackageSignature, compact, false);
    }

    public LambdaWeavingPlanBuilder(boolean consolidateTier2ByPackageSam, boolean consolidateTier2ByPackage,
                                    boolean consolidateTier2ByPackageSignature, boolean compact, boolean directTier1) {
        this.consolidateTier2ByPackageSam = consolidateTier2ByPackageSam;
        this.consolidateTier2ByPackage = consolidateTier2ByPackage;
        this.consolidateTier2ByPackageSignature = consolidateTier2ByPackageSignature;
        this.rootPackageSignature = false;
        this.rootOrdinalBySiteKey = java.util.Map.of();
        this.compact = compact;
        this.directTier1 = directTier1;
    }

    /**
     * ROOT_PACKAGE_SIGNATURE mode (track T4): the map binds each site key to
     * its physical root ordinal from the frozen output-root list.
     */
    public LambdaWeavingPlanBuilder(boolean consolidateTier2ByPackageSam, boolean consolidateTier2ByPackage,
                                    boolean consolidateTier2ByPackageSignature, boolean compact, boolean directTier1,
                                    boolean rootPackageSignature, java.util.Map<String, Integer> rootOrdinalBySiteKey) {
        this.consolidateTier2ByPackageSam = consolidateTier2ByPackageSam;
        this.consolidateTier2ByPackage = consolidateTier2ByPackage;
        this.consolidateTier2ByPackageSignature = consolidateTier2ByPackageSignature;
        this.rootPackageSignature = rootPackageSignature;
        this.rootOrdinalBySiteKey = rootOrdinalBySiteKey == null ? java.util.Map.of() : java.util.Map.copyOf(rootOrdinalBySiteKey);
        this.compact = compact;
        this.directTier1 = directTier1;
    }

    public LambdaWeavingPlan build(LambdaFilterResult filterResult, Tier1RuntimePlanResult runtimePlanResult) {
        Map<String, LambdaWeaveTarget> targets = new LinkedHashMap<>();

        for (Tier1RuntimePlan plan : runtimePlanResult.supportedPlans()) {
            targets.put(plan.decision().meta().siteKey(), directTier1
                ? LambdaWeaveTarget.tier1Direct(
                    plan.decision().meta().siteKey(),
                    plan.decision().meta().samInterfaceInternalName(),
                    Tier1DirectAdapterNamingStrategy.internalName(plan.decision().meta()),
                    Tier1DirectAdapterNamingStrategy.fieldName(plan.decision().meta())
                )
                : LambdaWeaveTarget.tier1(
                    plan.decision().meta().siteKey(),
                    plan.decision().meta().samInterfaceInternalName(),
                    plan.slotId(),
                    plan.adapterKind().factoryMethodName(),
                    plan.adapterKind().factoryMethodDescriptor()
                ));
        }

        filterResult.tier2Eligible().forEach(decision -> targets.put(
            decision.meta().siteKey(),
            LambdaWeaveTarget.tier2(
                decision.meta().siteKey(),
                decision.meta().samInterfaceInternalName(),
                tier2AdapterInternalName(decision),
                tier2AdapterFieldName(decision),
                decision.meta().implOwner(),
                decision.meta().implName(),
                decision.meta().implDesc(),
                requiresAccessWidening(decision)
            )
        ));

        return new LambdaWeavingPlan(targets);
    }

    private String tier2AdapterInternalName(com.yourorg.jmoa.plugin.filter.LambdaFilterDecision decision) {
        if (rootPackageSignature) {
            Integer rootOrdinal = rootOrdinalBySiteKey.get(decision.meta().siteKey());
            if (rootOrdinal == null || rootOrdinal < 0) {
                throw new IllegalArgumentException("ROOT_PACKAGE_SIGNATURE has no physical-root ordinal for site "
                        + decision.meta().siteKey());
            }
            return Tier2AdapterNamingStrategy.rootPackageSignatureInternalName(
                decision.meta(),
                rootOrdinal,
                compact);
        }
        if (consolidateTier2ByPackageSignature) {
            return compact
                ? Tier2AdapterNamingStrategy.compactPackageSignatureInternalName(decision.meta())
                : Tier2AdapterNamingStrategy.packageSignatureInternalName(decision.meta());
        }
        if (consolidateTier2ByPackage) {
            return Tier2AdapterNamingStrategy.packageOnlyInternalName(decision.meta());
        }
        if (consolidateTier2ByPackageSam) {
            return compact
                ? Tier2AdapterNamingStrategy.compactPackageSamInternalName(decision.meta())
                : Tier2AdapterNamingStrategy.packageSamInternalName(decision.meta());
        }
        return Tier2AdapterNamingStrategy.internalName(decision.meta());
    }

    private String tier2AdapterFieldName(com.yourorg.jmoa.plugin.filter.LambdaFilterDecision decision) {
        boolean consolidated = consolidateTier2ByPackageSam || consolidateTier2ByPackage
            || consolidateTier2ByPackageSignature || rootPackageSignature;
        if (!consolidated) {
            return "INSTANCE";
        }
        return compact
            ? Tier2AdapterNamingStrategy.compactFieldName(decision.meta())
            : Tier2AdapterNamingStrategy.fieldName(decision.meta());
    }

    private static boolean requiresAccessWidening(com.yourorg.jmoa.plugin.filter.LambdaFilterDecision decision) {
        return decision.accessVisibility() == com.yourorg.jmoa.plugin.dedup.AccessResolver.Visibility.PRIVATE
            && decision.meta().implTag() == Opcodes.H_INVOKESTATIC
            && decision.meta().implName().startsWith("lambda$");
    }
}
