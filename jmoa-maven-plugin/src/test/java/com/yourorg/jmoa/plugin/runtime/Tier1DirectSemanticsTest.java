package com.yourorg.jmoa.plugin.runtime;

import com.example.direct.DirectSemanticFixture;
import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaTier;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class Tier1DirectSemanticsTest {
    @Test
    void receiverNullTypeAndSingletonMatchMethodReference() throws Exception {
        LambdaFilterDecision d = decision("trim", RuntimeAdapterKind.FUNCTION, "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;", Opcodes.H_INVOKEVIRTUAL,
            "java/lang/String", "trim", "()Ljava/lang/String;", "(Ljava/lang/String;)Ljava/lang/String;");
        Field field = adapter(d, RuntimeAdapterKind.FUNCTION);
        @SuppressWarnings("unchecked") Function<Object, Object> f = (Function<Object, Object>) field.get(null);
        assertSame(f, field.get(null));
        assertEquals("patient", f.apply(" patient "));
        assertThrows(NullPointerException.class, () -> f.apply(null));
        assertThrows(ClassCastException.class, () -> f.apply(7));
    }

    @Test
    void targetExceptionAndReturnValuesPropagateWithoutWrapping() throws Exception {
        LambdaFilterDecision d = decision("exception", RuntimeAdapterKind.FUNCTION, "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;", Opcodes.H_INVOKESTATIC,
            "com/example/direct/DirectSemanticFixture", "requireValue", "(Ljava/lang/String;)Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/String;");
        @SuppressWarnings("unchecked") Function<String, String> f = (Function<String, String>) adapter(d, RuntimeAdapterKind.FUNCTION).get(null);
        assertEquals("ok", f.apply("ok"));
        assertNull(f.apply(null));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> f.apply("reject"));
        assertEquals("fixture rejection", error.getMessage());
    }

    @Test
    void constructorSupplierCreatesFreshTypedInstances() throws Exception {
        LambdaFilterDecision d = decision("constructor", RuntimeAdapterKind.SUPPLIER, "get",
            "()Ljava/lang/Object;", Opcodes.H_NEWINVOKESPECIAL,
            "com/example/direct/DirectSemanticFixture", "<init>", "()V", "()Lcom/example/direct/DirectSemanticFixture;");
        Field field = adapter(d, RuntimeAdapterKind.SUPPLIER);
        Supplier<?> s = (Supplier<?>) field.get(null);
        assertSame(s, field.get(null));
        assertInstanceOf(DirectSemanticFixture.class, s.get());
        assertNotSame(s.get(), s.get());
    }

    @Test
    void biConsumerPreservesTypedMapMutationAndTypeFailure() throws Exception {
        LambdaFilterDecision d = decision("store", RuntimeAdapterKind.BI_CONSUMER, "accept",
            "(Ljava/lang/Object;Ljava/lang/Object;)V", Opcodes.H_INVOKESTATIC,
            "com/example/direct/DirectSemanticFixture", "store", "(Ljava/util/Map;Ljava/lang/Object;)V",
            "(Ljava/util/Map;Ljava/lang/Object;)V");
        @SuppressWarnings("unchecked") BiConsumer<Object, Object> f = (BiConsumer<Object, Object>) adapter(d, RuntimeAdapterKind.BI_CONSUMER).get(null);
        Map<String, Object> map = new HashMap<>();
        Object value = new Object();
        f.accept(map, value);
        assertSame(value, map.get("fixture"));
        assertThrows(NullPointerException.class, () -> f.accept(null, value));
        assertThrows(ClassCastException.class, () -> f.accept("wrong map", value));
    }

    private static Field adapter(LambdaFilterDecision decision, RuntimeAdapterKind kind) throws Exception {
        List<Tier2AdapterArtifact> artifacts = new Tier2PackageAdapterGenerator(true)
            .generateTier1DirectAdapters(List.of(new Tier1RuntimePlan(0, kind, decision)), Opcodes.V22);
        assertEquals(1, artifacts.size());
        Tier2AdapterArtifact artifact = artifacts.get(0);
        Class<?> generated = new ClassLoader(Tier1DirectSemanticsTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(artifact.internalName().replace('/', '.'), artifact.classBytes(), 0, artifact.classBytes().length); }
        }.define();
        return generated.getField(Tier1DirectAdapterNamingStrategy.fieldName(decision.meta()));
    }

    private static LambdaFilterDecision decision(String key, RuntimeAdapterKind kind, String samName,
            String samDescriptor, int opcode, String owner, String member, String descriptor, String instantiated) {
        LambdaMeta meta = new LambdaMeta(key, "com/example/direct/Caller", "com/example/direct", "factory", "()V", 0,
            samName, "()L" + kind.samInterfaceInternalName() + ";", samDescriptor, false, false,
            opcode, owner, false, member, descriptor, instantiated, 3L);
        return LambdaFilterDecision.eligible(meta, true, false, 3L, LambdaTier.TIER1, AccessResolver.Visibility.PUBLIC);
    }
}
