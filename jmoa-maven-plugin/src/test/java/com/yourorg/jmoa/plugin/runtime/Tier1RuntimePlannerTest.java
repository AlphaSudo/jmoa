package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;
import com.yourorg.jmoa.plugin.filter.LambdaTier;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Tier1RuntimePlannerTest {

    @Test
    void plansSupportedTier1SitesDeterministically() {
        LambdaFilterDecision supplier = eligible("supplier-a", "()Ljava/util/function/Supplier;");
        LambdaFilterDecision function = eligible("function-b", "()Ljava/util/function/Function;");

        Tier1RuntimePlanResult result = new Tier1RuntimePlanner().plan(new LambdaFilterResult(List.of(function, supplier)));

        assertEquals(2, result.supportedPlans().size());
        assertEquals("function-b", result.supportedPlans().get(0).decision().meta().siteKey());
        assertEquals(0, result.supportedPlans().get(0).slotId());
        assertEquals(RuntimeAdapterKind.FUNCTION, result.supportedPlans().get(0).adapterKind());
        assertEquals("supplier-a", result.supportedPlans().get(1).decision().meta().siteKey());
        assertEquals(0, result.supportedPlans().get(1).slotId());
        assertEquals(RuntimeAdapterKind.SUPPLIER, result.supportedPlans().get(1).adapterKind());
    }

    @Test
    void assignsSlotIdsPerAdapterKind() {
        LambdaFilterDecision firstPredicate = eligible("predicate-a", "()Ljava/util/function/Predicate;");
        LambdaFilterDecision supplier = eligible("supplier-a", "()Ljava/util/function/Supplier;");
        LambdaFilterDecision secondPredicate = eligible("predicate-b", "()Ljava/util/function/Predicate;");

        Tier1RuntimePlanResult result = new Tier1RuntimePlanner().plan(
            new LambdaFilterResult(List.of(secondPredicate, supplier, firstPredicate))
        );

        assertEquals(3, result.supportedPlans().size());
        assertEquals("predicate-a", result.supportedPlans().get(0).decision().meta().siteKey());
        assertEquals(0, result.supportedPlans().get(0).slotId());
        assertEquals(RuntimeAdapterKind.PREDICATE, result.supportedPlans().get(0).adapterKind());
        assertEquals("predicate-b", result.supportedPlans().get(1).decision().meta().siteKey());
        assertEquals(1, result.supportedPlans().get(1).slotId());
        assertEquals(RuntimeAdapterKind.PREDICATE, result.supportedPlans().get(1).adapterKind());
        assertEquals("supplier-a", result.supportedPlans().get(2).decision().meta().siteKey());
        assertEquals(0, result.supportedPlans().get(2).slotId());
        assertEquals(RuntimeAdapterKind.SUPPLIER, result.supportedPlans().get(2).adapterKind());
    }

    @Test
    void separatesUnsupportedTier1SamShapes() {
        LambdaFilterDecision unsupported = eligible("callable-site", "()Ljava/util/concurrent/Callable;");

        Tier1RuntimePlanResult result = new Tier1RuntimePlanner().plan(new LambdaFilterResult(List.of(unsupported)));

        assertEquals(0, result.supportedPlans().size());
        assertEquals(1, result.unsupportedTier1Sites().size());
        assertEquals("callable-site", result.unsupportedTier1Sites().getFirst().meta().siteKey());
    }

    private static LambdaFilterDecision eligible(String siteKey, String indyFactoryDesc) {
        LambdaMeta meta = new LambdaMeta(
            siteKey,
            "com/example/PatientService",
            "com/example",
            "buildProfiles",
            "()V",
            0,
            "apply",
            indyFactoryDesc,
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            false,
            false,
            Opcodes.H_INVOKESTATIC,
            "java/lang/String",
            "valueOf",
            "(Ljava/lang/Object;)Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/Object;",
            1L
        );
        return LambdaFilterDecision.eligible(meta, true, false, 1L, LambdaTier.TIER1, AccessResolver.Visibility.PUBLIC);
    }
}
