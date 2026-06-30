package com.yourorg.jmoa.plugin.synth;

import com.yourorg.jmoa.plugin.dedup.AccessPlan;
import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.dedup.AccessTier;
import com.yourorg.jmoa.plugin.dedup.DeduplicationKey;
import com.yourorg.jmoa.plugin.dedup.SharedGroup;
import com.yourorg.jmoa.plugin.scanner.LambdaSite;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Field;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class ClassSynthesizerTest {

    static class ByteArrayClassLoader extends ClassLoader {
        public Class<?> define(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    @Test
    public void testSynthesizeFunction() throws Exception {
        DeduplicationKey key = new DeduplicationKey(
                "java/util/function/Function",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                Opcodes.H_INVOKEVIRTUAL,
                "java/lang/String",
                "length",
                "()I",
                "(Ljava/lang/String;)Ljava/lang/Integer;"
        );

        LambdaSite site = new LambdaSite(
                null, null, null, null, 0, "apply", "()Ljava/util/function/Function;",
                null, null, null
        );

        SharedGroup group = new SharedGroup(
                key,
                "LF_String_length_test",
                java.util.List.of(site),
                new AccessPlan(
                    AccessTier.TIER1_PUBLIC_LOOKUP,
                    AccessResolver.Visibility.PUBLIC,
                    false,
                    null,
                    "test"
                )
        );

        byte[] classBytes = ClassSynthesizer.synthesize(group);
        assertNotNull(classBytes);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        Class<?> synthClass = loader.define("LF_String_length_test", classBytes);
        assertNotNull(synthClass);

        Field instanceField = synthClass.getField("INSTANCE");
        assertNotNull(instanceField);
        Object instance = instanceField.get(null);
        assertNotNull(instance);
        assertTrue(instance instanceof Function);

        @SuppressWarnings("unchecked")
        Function<String, Integer> func = (Function<String, Integer>) instance;
        assertEquals(5, func.apply("hello"));
    }
}
