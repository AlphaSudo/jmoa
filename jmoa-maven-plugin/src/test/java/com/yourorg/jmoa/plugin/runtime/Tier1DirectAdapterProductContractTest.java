package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;
import com.yourorg.jmoa.plugin.filter.LambdaTier;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import com.yourorg.jmoa.plugin.weave.LambdaWeaveTarget;
import com.yourorg.jmoa.plugin.weave.LambdaWeavingPlan;
import com.yourorg.jmoa.plugin.weave.LambdaWeavingPlanBuilder;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Track T2 product contract for direct Tier 1: every supported site becomes
 * exactly one runtime-free direct adapter class, no Tier-1 registry or
 * cache/factory machinery is generated or referenced, every call site weaves
 * to TIER1_DIRECT_ADAPTER, and generation is byte-deterministic.
 */
class Tier1DirectAdapterProductContractTest {

    @Test
    void fourDirectSitesProduceFourRuntimeFreeAdaptersAndOnlyDirectWeaveTargets() {
        List<LambdaFilterDecision> decisions = fourDistinctSites();
        List<Tier1RuntimePlan> plans = decisions.stream()
                .map(decision -> new Tier1RuntimePlan(0, RuntimeAdapterKind.FUNCTION, decision))
                .toList();
        Tier1RuntimePlanResult planResult = new Tier1RuntimePlanResult(plans, List.of());

        Tier2PackageAdapterGenerator generator = new Tier2PackageAdapterGenerator(true);
        List<Tier2AdapterArtifact> artifacts = generator.generateTier1DirectAdapters(planResult.supportedPlans(), Opcodes.V22);

        assertEquals(4, artifacts.size(), "four supported sites must produce exactly four direct adapter classes");
        for (Tier2AdapterArtifact artifact : artifacts) {
            assertTrue(artifact.internalName().startsWith("jmoa/runtime/JmoaDirectAdapters$"),
                    "direct adapters live in the JMOA-owned namespace");
            assertFalse(containsText(artifact.classBytes(), "jmoa/runtime/JmoaRuntime\""),
                    "no generated Tier-1 registry class may be referenced");
            assertFalse(containsText(artifact.classBytes(), "jmoa/runtime/JmoaRuntimeSupport"),
                    "no runtime cache/factory support may be referenced");
            assertFalse(containsText(artifact.classBytes(), "java/lang/invoke/MethodHandle"),
                    "direct adapters must not carry MethodHandle machinery");
        }

        LambdaWeavingPlan weaving = new LambdaWeavingPlanBuilder(false, false, false, true, true)
                .build(new LambdaFilterResult(decisions), planResult);
        for (LambdaFilterDecision decision : decisions) {
            LambdaWeaveTarget target = weaving.targetFor(decision.meta().siteKey());
            assertNotNull(target, "every supported site must weave: " + decision.meta().siteKey());
            assertEquals(LambdaWeaveTarget.WeaveKind.TIER1_DIRECT_ADAPTER, target.kind(),
                    "direct Tier-1 call sites never weave to a runtime factory");
        }
        assertEquals(4, distinctAdapterInternalNames(artifacts).size(),
                "adapter class names must be unique per site");
    }

    @Test
    void directGenerationIsByteDeterministic() {
        List<Tier1RuntimePlan> plans = fourDistinctSites().stream()
                .map(decision -> new Tier1RuntimePlan(0, RuntimeAdapterKind.FUNCTION, decision))
                .toList();
        Tier1RuntimePlanResult planResult = new Tier1RuntimePlanResult(plans, List.of());
        List<Tier2AdapterArtifact> first = new Tier2PackageAdapterGenerator(true)
                .generateTier1DirectAdapters(planResult.supportedPlans(), Opcodes.V22);
        List<Tier2AdapterArtifact> second = new Tier2PackageAdapterGenerator(true)
                .generateTier1DirectAdapters(planResult.supportedPlans(), Opcodes.V22);
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).internalName(), second.get(i).internalName());
            assertArrayEquals(first.get(i).classBytes(), second.get(i).classBytes(),
                    "direct adapter generation must be byte-identical across runs");
        }
    }

    private static List<String> distinctAdapterInternalNames(List<Tier2AdapterArtifact> artifacts) {
        List<String> names = new ArrayList<>();
        for (Tier2AdapterArtifact artifact : artifacts) {
            if (!names.contains(artifact.internalName())) {
                names.add(artifact.internalName());
            }
        }
        return names;
    }

    private static boolean containsText(byte[] classBytes, String text) {
        byte[] needle = text.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= classBytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (classBytes[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static List<LambdaFilterDecision> fourDistinctSites() {
        // The direct-adapter class name is keyed by the erased SAM descriptor,
        // so four distinct SAM shapes must yield four adapter classes (the
        // real product candidate's "four direct Tier-1 classes").
        String[] samDescriptors = {
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                "(J)Ljava/lang/Object;",
                "(Z)Ljava/lang/Object;"
        };
        List<LambdaFilterDecision> decisions = new ArrayList<>();
        for (int ordinal = 1; ordinal <= samDescriptors.length; ordinal++) {
            LambdaMeta meta = new LambdaMeta(
                    "product-contract-site-" + ordinal,
                    "com/example/ProductCaller",
                    "com/example",
                    "run",
                    "()V",
                    ordinal,
                    "apply",
                    "()Ljava/util/function/Function;",
                    samDescriptors[ordinal - 1],
                    false,
                    false,
                    Opcodes.H_INVOKEVIRTUAL,
                    "java/lang/String",
                    false,
                    "trim",
                    "()Ljava/lang/String;",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    (long) ordinal);
            decisions.add(LambdaFilterDecision.eligible(meta, true, false, (long) ordinal, LambdaTier.TIER1, AccessResolver.Visibility.PUBLIC));
        }
        return decisions;
    }
}
