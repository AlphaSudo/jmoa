package com.yourorg.jmoa.plugin.weave;

import com.yourorg.jmoa.plugin.scanner.LambdaSite;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaWeavingCoordinatorTest {

    @Test
    void tolerantModeWarnsAndSkipsWhenRewriteFails() throws Exception {
        LambdaSite site = missingSite();
        LambdaWeavingPlan plan = new LambdaWeavingPlan(java.util.Map.of(
            site.siteKey(),
            LambdaWeaveTarget.tier2(
                site.siteKey(),
                "java/util/function/Function",
                "com/example/MissingAdapter",
                "INSTANCE",
                site.implHandle().getOwner(),
                site.implHandle().getName(),
                site.implHandle().getDesc(),
                false
            )
        ));

        List<String> warnings = new ArrayList<>();
        WeaveExecutionResult result = new LambdaWeavingCoordinator().rewriteEligibleClasses(
            List.of(site),
            plan,
            getClass().getClassLoader(),
            false,
            warnings::add
        );

        assertEquals(1, result.plannedSites());
        assertEquals(1, result.targetedClasses());
        assertEquals(0, result.rewrittenClasses());
        assertEquals(1, result.failedClasses());
        assertEquals(0, result.sanitySummary().verifiedEligibleSites());
        assertEquals(1, result.sanitySummary().unverifiedEligibleSites());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("Skipping class rewrite"));
    }

    @Test
    void failFastModeThrowsWhenRewriteFails() {
        LambdaSite site = missingSite();
        LambdaWeavingPlan plan = new LambdaWeavingPlan(java.util.Map.of(
            site.siteKey(),
            LambdaWeaveTarget.tier1(site.siteKey(), "java/util/function/Function", 0, "createFunction", "(I)Ljava/util/function/Function;")
        ));

        IOException error = assertThrows(IOException.class, () -> new LambdaWeavingCoordinator().rewriteEligibleClasses(
            List.of(site),
            plan,
            getClass().getClassLoader(),
            true,
            ignored -> { }
        ));
        assertTrue(error.getMessage().contains("Failed to rewrite class"));
    }

    private static LambdaSite missingSite() {
        Handle implHandle = new Handle(
            Opcodes.H_INVOKESTATIC,
            "com/example/MissingOwner",
            "missing",
            "(Ljava/lang/String;)Ljava/lang/String;",
            false
        );
        ClassNode classNode = new ClassNode();
        classNode.name = "com/example/MissingFixture";
        MethodNode methodNode = new MethodNode();
        methodNode.name = "build";
        methodNode.desc = "()V";
        InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
            "apply",
            "()Ljava/util/function/Function;",
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "()V",
                false
            ),
            Type.getMethodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
            implHandle,
            Type.getMethodType("(Ljava/lang/String;)Ljava/lang/String;")
        );
        return new LambdaSite(
            new File(System.getProperty("java.io.tmpdir"), "jmoa-definitely-missing/NoSuchClass.class"),
            classNode,
            methodNode,
            indy,
            0,
            "apply",
            "()Ljava/util/function/Function;",
            Type.getMethodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
            implHandle,
            Type.getMethodType("(Ljava/lang/String;)Ljava/lang/String;")
        );
    }
}
