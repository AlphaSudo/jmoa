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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Tier1DirectAdapterGeneratorTest {

    @Test
    void generatesRuntimeFreeDirectAdapterAndDirectWeaveTarget() throws Exception {
        LambdaFilterDecision decision = publicStringTrimDecision();
        Tier1RuntimePlan plan = new Tier1RuntimePlan(0, RuntimeAdapterKind.FUNCTION, decision);
        Tier1RuntimePlanResult plans = new Tier1RuntimePlanResult(List.of(plan), List.of());

        List<Tier2AdapterArtifact> artifacts = new Tier2PackageAdapterGenerator(true)
            .generateTier1DirectAdapters(plans.supportedPlans(), Opcodes.V22);

        assertEquals(1, artifacts.size());
        Tier2AdapterArtifact artifact = artifacts.getFirst();
        assertTrue(artifact.internalName().startsWith("jmoa/runtime/JmoaDirectAdapters$"));
        assertTrue(hasDirectInvoke(artifact.classBytes(), Opcodes.INVOKEVIRTUAL, "java/lang/String", "trim"));
        assertFalse(referencesOwner(artifact.classBytes(), "jmoa/runtime/JmoaRuntimeSupport"));
        assertFalse(referencesOwner(artifact.classBytes(), "java/lang/invoke/MethodHandle"));

        ByteArrayLoader loader = new ByteArrayLoader(getClass().getClassLoader());
        Class<?> generated = loader.define(artifact.internalName().replace('/', '.'), artifact.classBytes());
        Field field = generated.getField(Tier1DirectAdapterNamingStrategy.fieldName(decision.meta()));
        @SuppressWarnings("unchecked")
        Function<String, String> function = (Function<String, String>) field.get(null);
        assertEquals("patient", function.apply(" patient "));

        LambdaWeavingPlan weaving = new LambdaWeavingPlanBuilder(false, false, false, true, true)
            .build(new LambdaFilterResult(List.of(decision)), plans);
        LambdaWeaveTarget target = weaving.targetFor(decision.meta().siteKey());
        assertNotNull(target);
        assertEquals(LambdaWeaveTarget.WeaveKind.TIER1_DIRECT_ADAPTER, target.kind());
        assertEquals(artifact.internalName(), target.tier2AdapterInternalName());
        assertEquals(Tier1DirectAdapterNamingStrategy.fieldName(decision.meta()), target.tier2AdapterFieldName());
    }

    private static LambdaFilterDecision publicStringTrimDecision() {
        LambdaMeta meta = new LambdaMeta(
            "tier1-direct-string-trim",
            "com/example/Caller",
            "com/example",
            "build",
            "()V",
            1,
            "apply",
            "()Ljava/util/function/Function;",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            false,
            false,
            Opcodes.H_INVOKEVIRTUAL,
            "java/lang/String",
            false,
            "trim",
            "()Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/String;",
            3L
        );
        return LambdaFilterDecision.eligible(meta, true, false, 3L, LambdaTier.TIER1, AccessResolver.Visibility.PUBLIC);
    }

    private static boolean hasDirectInvoke(byte[] bytes, int opcode, String owner, String name) {
        AtomicBoolean found = new AtomicBoolean();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int seenOpcode, String seenOwner, String seenName, String desc, boolean isInterface) {
                        if (seenOpcode == opcode && seenOwner.equals(owner) && seenName.equals(name)) {
                            found.set(true);
                        }
                    }
                };
            }
        }, 0);
        return found.get();
    }

    private static boolean referencesOwner(byte[] bytes, String owner) {
        AtomicBoolean found = new AtomicBoolean();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String seenOwner, String methodName, String desc, boolean isInterface) {
                        if (seenOwner.equals(owner)) {
                            found.set(true);
                        }
                    }
                };
            }
        }, 0);
        return found.get();
    }

    private static final class ByteArrayLoader extends ClassLoader {
        private ByteArrayLoader(ClassLoader parent) {
            super(parent);
        }

        private Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
