package jmoa.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmoaRuntimeSupportTest {

    public static final class PublicTargets {
        public static String trimToUpper(String value) {
            return value == null ? null : value.trim().toUpperCase();
        }

        public static boolean longerThanThree(String value) {
            return value != null && value.length() > 3;
        }

        public static String constantLabel() {
            return "patient-profile";
        }

        public static void rememberLength(Map<String, Integer> sink, String value) {
            sink.put(value, value.length());
        }

        public static void mergeLengths(Map<String, Integer> sink, String key, Integer length) {
            sink.put(key, length);
        }
    }

    @AfterEach
    void tearDown() {
        JmoaRuntimeSupport.resetForTests();
    }

    @Test
    void executesPublicTier1TargetThroughFactory() throws Throwable {
        MethodHandle target = MethodHandles.publicLookup().findStatic(
            PublicTargets.class,
            "trimToUpper",
            MethodType.methodType(String.class, String.class)
        );

        JmoaRuntimeSupport.installFunctionTargets(new MethodHandle[]{target});

        @SuppressWarnings("unchecked")
        Function<String, String> function = (Function<String, String>) (Function<?, ?>) JmoaFactory.createFunction(0);

        assertEquals("HELLO", function.apply(" hello "));
        assertEquals(1, JmoaRuntimeSupport.functionCount());
    }

    @Test
    void reusesSingletonAdapterInstancesPerId() throws Throwable {
        MethodHandle target = MethodHandles.publicLookup().findStatic(
            PublicTargets.class,
            "trimToUpper",
            MethodType.methodType(String.class, String.class)
        );

        JmoaRuntimeSupport.installFunctionTargets(new MethodHandle[]{target});

        Function<Object, Object> first = JmoaFactory.createFunction(0);
        Function<Object, Object> second = JmoaFactory.createFunction(0);

        assertSame(first, second);
    }

    @Test
    void executesOtherSupportedSamAdapters() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Map<String, Integer> consumerSink = new LinkedHashMap<>();
        Map<String, Integer> biConsumerSink = new LinkedHashMap<>();
        MethodHandle predicateTarget = lookup.findStatic(
            PublicTargets.class,
            "longerThanThree",
            MethodType.methodType(boolean.class, String.class)
        );
        MethodHandle supplierTarget = lookup.findStatic(
            PublicTargets.class,
            "constantLabel",
            MethodType.methodType(String.class)
        );
        MethodHandle consumerTarget = lookup.findStatic(
            PublicTargets.class,
            "rememberLength",
            MethodType.methodType(void.class, Map.class, String.class)
        ).bindTo(consumerSink);
        MethodHandle biConsumerTarget = lookup.findStatic(
            PublicTargets.class,
            "mergeLengths",
            MethodType.methodType(void.class, Map.class, String.class, Integer.class)
        ).bindTo(biConsumerSink);

        JmoaRuntimeSupport.installPredicateTargets(new MethodHandle[]{predicateTarget});
        JmoaRuntimeSupport.installSupplierTargets(new MethodHandle[]{supplierTarget});
        JmoaRuntimeSupport.installConsumerTargets(new MethodHandle[]{consumerTarget});
        JmoaRuntimeSupport.installBiConsumerTargets(new MethodHandle[]{biConsumerTarget});

        @SuppressWarnings("unchecked")
        Predicate<String> predicate = (Predicate<String>) (Predicate<?>) JmoaFactory.createPredicate(0);
        @SuppressWarnings("unchecked")
        Supplier<String> supplier = (Supplier<String>) (Supplier<?>) JmoaFactory.createSupplier(0);
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = (Consumer<String>) (Consumer<?>) JmoaFactory.createConsumer(0);
        @SuppressWarnings("unchecked")
        BiConsumer<String, Integer> biConsumer = (BiConsumer<String, Integer>) (BiConsumer<?, ?>) JmoaFactory.createBiConsumer(0);

        assertTrue(predicate.test("hello"));
        assertEquals("patient-profile", supplier.get());
        consumer.accept("alpha");
        biConsumer.accept("beta", 4);
        assertEquals(5, consumerSink.get("alpha"));
        assertEquals(4, biConsumerSink.get("beta"));
        assertSame(JmoaFactory.createPredicate(0), JmoaFactory.createPredicate(0));
        assertSame(JmoaFactory.createSupplier(0), JmoaFactory.createSupplier(0));
        assertSame(JmoaFactory.createConsumer(0), JmoaFactory.createConsumer(0));
        assertSame(JmoaFactory.createBiConsumer(0), JmoaFactory.createBiConsumer(0));
    }
}
