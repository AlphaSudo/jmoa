package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaTier;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import jmoa.runtime.JmoaRuntimeSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class Tier1RuntimeClassGeneratorTest {

    public static final class PublicTargets {
        private static final Map<String, Integer> CONSUMER_SINK = new LinkedHashMap<>();

        public static boolean longerThanThree(String value) {
            return value != null && value.length() > 3;
        }

        public static String upper(String value) {
            return value == null ? null : value.toUpperCase();
        }

        public static void rememberLength(String value) {
            CONSUMER_SINK.put(value, value.length());
        }
    }

    static class GeneratedClassLoader extends ClassLoader {
        GeneratedClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    @AfterEach
    void tearDown() {
        JmoaRuntimeSupport.reset();
        PublicTargets.CONSUMER_SINK.clear();
    }

    @Test
    void generatesLoadableRuntimeClassAndInstallsTargets() throws Throwable {
        Tier1RuntimePlanResult planResult = new Tier1RuntimePlanResult(
            List.of(
                new Tier1RuntimePlan(0, RuntimeAdapterKind.SUPPLIER, decision(
                    "supplier-site",
                    "get",
                    "()Ljava/util/function/Supplier;",
                    Opcodes.H_NEWINVOKESPECIAL,
                    "java/util/LinkedHashMap",
                    "<init>",
                    "()V"
                )),
                new Tier1RuntimePlan(0, RuntimeAdapterKind.BI_CONSUMER, decision(
                    "bi-consumer-site",
                    "accept",
                    "()Ljava/util/function/BiConsumer;",
                    Opcodes.H_INVOKEINTERFACE,
                    "java/util/Map",
                    "putAll",
                    "(Ljava/util/Map;)V"
                )),
                new Tier1RuntimePlan(0, RuntimeAdapterKind.PREDICATE, decision(
                    "predicate-site",
                    "test",
                    "()Ljava/util/function/Predicate;",
                    Opcodes.H_INVOKESTATIC,
                    Type.getInternalName(PublicTargets.class),
                    "longerThanThree",
                    "(Ljava/lang/String;)Z"
                )),
                new Tier1RuntimePlan(0, RuntimeAdapterKind.FUNCTION, decision(
                    "function-site",
                    "apply",
                    "()Ljava/util/function/Function;",
                    Opcodes.H_INVOKESTATIC,
                    Type.getInternalName(PublicTargets.class),
                    "upper",
                    "(Ljava/lang/String;)Ljava/lang/String;"
                )),
                new Tier1RuntimePlan(0, RuntimeAdapterKind.CONSUMER, decision(
                    "consumer-site",
                    "accept",
                    "()Ljava/util/function/Consumer;",
                    Opcodes.H_INVOKESTATIC,
                    Type.getInternalName(PublicTargets.class),
                    "rememberLength",
                    "(Ljava/lang/String;)V"
                ))
            ),
            List.of()
        );

        Tier1RuntimeClassGenerator generator = new Tier1RuntimeClassGenerator();
        byte[] classBytes = generator.generate(planResult, getClass().getClassLoader());
        assertNotNull(classBytes);

        CheckClassAdapter.verify(new org.objectweb.asm.ClassReader(classBytes), getClass().getClassLoader(), false, new PrintWriter(System.out));

        GeneratedClassLoader loader = new GeneratedClassLoader(getClass().getClassLoader());
        Class<?> generated = loader.define("jmoa.runtime.JmoaRuntime", classBytes);
        assertNotNull(generated);

        Method supplierCount = generated.getMethod("supplierCount");
        Method biConsumerCount = generated.getMethod("biConsumerCount");
        Method predicateCount = generated.getMethod("predicateCount");
        Method functionCount = generated.getMethod("functionCount");
        Method consumerCount = generated.getMethod("consumerCount");
        assertEquals(1, supplierCount.invoke(null));
        assertEquals(1, biConsumerCount.invoke(null));
        assertEquals(1, predicateCount.invoke(null));
        assertEquals(1, functionCount.invoke(null));
        assertEquals(1, consumerCount.invoke(null));

        MethodHandle supplierTarget = JmoaRuntimeSupport.supplierTargets()[0];
        Object created = supplierTarget.invoke();
        assertInstanceOf(LinkedHashMap.class, created);

        MethodHandle predicateTarget = JmoaRuntimeSupport.predicateTargets()[0];
        assertEquals(true, predicateTarget.invoke("hello"));

        MethodHandle biConsumerTarget = JmoaRuntimeSupport.biConsumerTargets()[0];
        Map<String, Integer> target = new LinkedHashMap<>();
        Map<String, Integer> source = Map.of("a", 1);
        biConsumerTarget.invoke(target, source);
        assertEquals(source, target);

        Method invokeSupplier = generated.getMethod("invokeSupplier", int.class);
        Object generatedSupplierValue = invokeSupplier.invoke(null, 0);
        assertInstanceOf(LinkedHashMap.class, generatedSupplierValue);

        Method invokePredicate = generated.getMethod("invokePredicate", int.class, Object.class);
        assertEquals(true, invokePredicate.invoke(null, 0, "hello"));

        Method invokeFunction = generated.getMethod("invokeFunction", int.class, Object.class);
        assertEquals("HELLO", invokeFunction.invoke(null, 0, "hello"));

        Method invokeConsumer = generated.getMethod("invokeConsumer", int.class, Object.class);
        invokeConsumer.invoke(null, 0, "beta");
        assertEquals(4, PublicTargets.CONSUMER_SINK.get("beta"));

        Method invokeBiConsumer = generated.getMethod("invokeBiConsumer", int.class, Object.class, Object.class);
        Map<String, Integer> generatedTarget = new LinkedHashMap<>();
        invokeBiConsumer.invoke(null, 0, generatedTarget, source);
        assertEquals(source, generatedTarget);

        Method createSupplier = generated.getMethod("createSupplier", int.class);
        Supplier<?> cachedSupplier = (Supplier<?>) createSupplier.invoke(null, 0);
        assertSame(cachedSupplier, createSupplier.invoke(null, 0));
        assertInstanceOf(LinkedHashMap.class, cachedSupplier.get());

        Method createPredicate = generated.getMethod("createPredicate", int.class);
        Predicate<Object> cachedPredicate = cast(createPredicate.invoke(null, 0));
        assertSame(cachedPredicate, createPredicate.invoke(null, 0));
        assertEquals(true, cachedPredicate.test("hello"));

        Method createFunction = generated.getMethod("createFunction", int.class);
        Function<Object, Object> cachedFunction = cast(createFunction.invoke(null, 0));
        assertSame(cachedFunction, createFunction.invoke(null, 0));
        assertEquals("HELLO", cachedFunction.apply("hello"));

        Method createConsumer = generated.getMethod("createConsumer", int.class);
        Consumer<Object> cachedConsumer = cast(createConsumer.invoke(null, 0));
        assertSame(cachedConsumer, createConsumer.invoke(null, 0));
        cachedConsumer.accept("gamma");
        assertEquals(5, PublicTargets.CONSUMER_SINK.get("gamma"));

        Method createBiConsumer = generated.getMethod("createBiConsumer", int.class);
        BiConsumer<Object, Object> cachedBiConsumer = cast(createBiConsumer.invoke(null, 0));
        assertSame(cachedBiConsumer, createBiConsumer.invoke(null, 0));
        Map<String, Integer> cachedTarget = new LinkedHashMap<>();
        cachedBiConsumer.accept(cachedTarget, source);
        assertEquals(source, cachedTarget);
    }

    @Test
    void writesGeneratedClassToExpectedLocation() throws Exception {
        Tier1RuntimePlanResult planResult = new Tier1RuntimePlanResult(List.of(), List.of());
        Tier1RuntimeClassGenerator generator = new Tier1RuntimeClassGenerator();
        byte[] classBytes = generator.generate(planResult, getClass().getClassLoader());
        var outputDir = Files.createTempDirectory("jmoa-runtime-gen").toFile();

        generator.writeTo(outputDir, classBytes);

        assertEquals(true, Files.exists(outputDir.toPath().resolve("jmoa/runtime/JmoaRuntime.class")));
    }

    @Test
    void emitsRequestedClassFileVersion() {
        Tier1RuntimePlanResult planResult = new Tier1RuntimePlanResult(List.of(), List.of());
        Tier1RuntimeClassGenerator generator = new Tier1RuntimeClassGenerator();

        byte[] classBytes = generator.generate(planResult, getClass().getClassLoader(), Opcodes.V26);

        int majorVersion = ((classBytes[6] & 0xFF) << 8) | (classBytes[7] & 0xFF);
        assertEquals(Opcodes.V26, majorVersion);
    }

    @Test
    void keepsVarargsFunctionTargetsFixedArity() throws Throwable {
        Tier1RuntimePlanResult planResult = new Tier1RuntimePlanResult(
            List.of(new Tier1RuntimePlan(0, RuntimeAdapterKind.FUNCTION, decision(
                "varargs-function-site",
                "apply",
                "()Ljava/util/function/Function;",
                Opcodes.H_INVOKESTATIC,
                "java/util/Arrays",
                "asList",
                "([Ljava/lang/Object;)Ljava/util/List;"
            ))),
            List.of()
        );

        byte[] classBytes = new Tier1RuntimeClassGenerator().generate(planResult, getClass().getClassLoader());
        GeneratedClassLoader loader = new GeneratedClassLoader(getClass().getClassLoader());
        Class<?> generated = loader.define("jmoa.runtime.JmoaRuntime", classBytes);

        Method createFunction = generated.getMethod("createFunction", int.class);
        Function<Object, Object> function = cast(createFunction.invoke(null, 0));
        Object value = function.apply(new String[]{"alpha", "beta"});

        assertEquals(Arrays.asList("alpha", "beta"), value);
    }

    private static LambdaFilterDecision decision(
        String siteKey,
        String indyName,
        String indyFactoryDesc,
        int implTag,
        String implOwner,
        String implName,
        String implDesc
    ) {
        LambdaMeta meta = new LambdaMeta(
            siteKey,
            "com/example/PatientService",
            "com/example",
            "buildProfiles",
            "()V",
            0,
            indyName,
            indyFactoryDesc,
            samMethodTypeFor(indyFactoryDesc),
            false,
            false,
            implTag,
            implOwner,
            implName,
            implDesc,
            instantiatedTypeFor(implTag, implOwner, implDesc),
            1L
        );
        return LambdaFilterDecision.eligible(meta, true, false, 1L, LambdaTier.TIER1, AccessResolver.Visibility.PUBLIC);
    }

    private static String samMethodTypeFor(String indyFactoryDesc) {
        return switch (Type.getReturnType(indyFactoryDesc).getInternalName()) {
            case "java/util/function/Supplier" -> "()Ljava/lang/Object;";
            case "java/util/function/BiConsumer" -> "(Ljava/lang/Object;Ljava/lang/Object;)V";
            case "java/util/function/Predicate" -> "(Ljava/lang/Object;)Z";
            case "java/util/function/Consumer" -> "(Ljava/lang/Object;)V";
            default -> "(Ljava/lang/Object;)Ljava/lang/Object;";
        };
    }

    private static String instantiatedTypeFor(int implTag, String implOwner, String implDesc) {
        return switch (implTag) {
            case Opcodes.H_NEWINVOKESPECIAL -> Type.getMethodDescriptor(Type.getObjectType(implOwner), Type.getArgumentTypes(implDesc));
            case Opcodes.H_INVOKEINTERFACE -> "(Ljava/util/Map;Ljava/util/Map;)V";
            case Opcodes.H_INVOKESTATIC -> implDesc;
            default -> implDesc;
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
