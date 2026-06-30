package jmoa.runtime;

import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class JmoaRuntimeSupport {

    private static volatile MethodHandle[] functionTargets = new MethodHandle[0];
    private static volatile MethodHandle[] predicateTargets = new MethodHandle[0];
    private static volatile MethodHandle[] supplierTargets = new MethodHandle[0];
    private static volatile MethodHandle[] consumerTargets = new MethodHandle[0];
    private static volatile MethodHandle[] biConsumerTargets = new MethodHandle[0];
    private static volatile Function<?, ?>[] functionCache = new Function[0];
    private static volatile Predicate<?>[] predicateCache = new Predicate[0];
    private static volatile Supplier<?>[] supplierCache = new Supplier[0];
    private static volatile Consumer<?>[] consumerCache = new Consumer[0];
    private static volatile BiConsumer<?, ?>[] biConsumerCache = new BiConsumer[0];

    private JmoaRuntimeSupport() {
    }

    public static synchronized void installFunctionTargets(MethodHandle[] targets) {
        MethodHandle[] copy = validateTargets(targets, "Function");
        functionTargets = copy;
        functionCache = new Function[copy.length];
    }

    public static synchronized void installPredicateTargets(MethodHandle[] targets) {
        MethodHandle[] copy = validateTargets(targets, "Predicate");
        predicateTargets = copy;
        predicateCache = new Predicate[copy.length];
    }

    public static synchronized void installSupplierTargets(MethodHandle[] targets) {
        MethodHandle[] copy = validateTargets(targets, "Supplier");
        supplierTargets = copy;
        supplierCache = new Supplier[copy.length];
    }

    public static synchronized void installConsumerTargets(MethodHandle[] targets) {
        MethodHandle[] copy = validateTargets(targets, "Consumer");
        consumerTargets = copy;
        consumerCache = new Consumer[copy.length];
    }

    public static synchronized void installBiConsumerTargets(MethodHandle[] targets) {
        MethodHandle[] copy = validateTargets(targets, "BiConsumer");
        biConsumerTargets = copy;
        biConsumerCache = new BiConsumer[copy.length];
    }

    public static Object invokeFunction(int id, Object arg) {
        MethodHandle target = targetAt(functionTargets, id, "Function");
        try {
            return target.invoke(arg);
        } catch (Throwable throwable) {
            throw sneakyThrow(throwable);
        }
    }

    public static boolean invokePredicate(int id, Object arg) {
        MethodHandle target = targetAt(predicateTargets, id, "Predicate");
        try {
            return (boolean) target.invoke(arg);
        } catch (Throwable throwable) {
            throw sneakyThrow(throwable);
        }
    }

    public static Object invokeSupplier(int id) {
        MethodHandle target = targetAt(supplierTargets, id, "Supplier");
        try {
            return target.invoke();
        } catch (Throwable throwable) {
            throw sneakyThrow(throwable);
        }
    }

    public static void invokeConsumer(int id, Object arg) {
        MethodHandle target = targetAt(consumerTargets, id, "Consumer");
        try {
            target.invoke(arg);
        } catch (Throwable throwable) {
            throw sneakyThrow(throwable);
        }
    }

    public static void invokeBiConsumer(int id, Object left, Object right) {
        MethodHandle target = targetAt(biConsumerTargets, id, "BiConsumer");
        try {
            target.invoke(left, right);
        } catch (Throwable throwable) {
            throw sneakyThrow(throwable);
        }
    }

    public static Function<Object, Object> functionAdapter(int id) {
        return cachedAdapter(functionCache, id, JmoaFunctionAdapter::new, "Function");
    }

    public static Predicate<Object> predicateAdapter(int id) {
        return cachedAdapter(predicateCache, id, JmoaPredicateAdapter::new, "Predicate");
    }

    public static Supplier<Object> supplierAdapter(int id) {
        return cachedAdapter(supplierCache, id, JmoaSupplierAdapter::new, "Supplier");
    }

    public static Consumer<Object> consumerAdapter(int id) {
        return cachedAdapter(consumerCache, id, JmoaConsumerAdapter::new, "Consumer");
    }

    public static BiConsumer<Object, Object> biConsumerAdapter(int id) {
        return cachedAdapter(biConsumerCache, id, JmoaBiConsumerAdapter::new, "BiConsumer");
    }

    public static int functionCount() {
        return functionTargets.length;
    }

    static synchronized void resetForTests() {
        functionTargets = new MethodHandle[0];
        predicateTargets = new MethodHandle[0];
        supplierTargets = new MethodHandle[0];
        consumerTargets = new MethodHandle[0];
        biConsumerTargets = new MethodHandle[0];
        functionCache = new Function[0];
        predicateCache = new Predicate[0];
        supplierCache = new Supplier[0];
        consumerCache = new Consumer[0];
        biConsumerCache = new BiConsumer[0];
    }

    private static MethodHandle[] validateTargets(MethodHandle[] targets, String label) {
        MethodHandle[] copy = targets == null ? new MethodHandle[0] : Arrays.copyOf(targets, targets.length);
        for (int i = 0; i < copy.length; i++) {
            Objects.requireNonNull(copy[i], label + " target at index " + i + " must not be null");
        }
        return copy;
    }

    private static MethodHandle targetAt(MethodHandle[] targets, int id, String label) {
        if (id < 0 || id >= targets.length) {
            throw new IllegalArgumentException("Unknown " + label + " id: " + id);
        }
        return targets[id];
    }

    @SuppressWarnings("unchecked")
    private static <T> T cachedAdapter(Object[] cache, int id, java.util.function.IntFunction<T> creator, String label) {
        if (id < 0 || id >= cache.length) {
            throw new IllegalArgumentException("Unknown " + label + " id: " + id);
        }
        Object existing = cache[id];
        if (existing != null) {
            return (T) existing;
        }
        synchronized (JmoaRuntimeSupport.class) {
            Object resolved = cache[id];
            if (resolved == null) {
                resolved = creator.apply(id);
                cache[id] = resolved;
            }
            return (T) resolved;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }
}
