package jmoa.runtime;

import java.lang.invoke.MethodHandle;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class JmoaRuntimeSupport {

    private static MethodHandle[] functionTargets = new MethodHandle[0];
    private static MethodHandle[] predicateTargets = new MethodHandle[0];
    private static MethodHandle[] supplierTargets = new MethodHandle[0];
    private static MethodHandle[] consumerTargets = new MethodHandle[0];
    private static MethodHandle[] biConsumerTargets = new MethodHandle[0];
    private static Function<?, ?>[] functionCache = new Function[0];
    private static Predicate<?>[] predicateCache = new Predicate[0];
    private static Supplier<?>[] supplierCache = new Supplier[0];
    private static Consumer<?>[] consumerCache = new Consumer[0];
    private static BiConsumer<?, ?>[] biConsumerCache = new BiConsumer[0];

    private JmoaRuntimeSupport() {
    }

    public static void installFunctionTargets(MethodHandle[] targets) {
        functionTargets = targets;
        functionCache = new Function[targets.length];
    }

    public static void installPredicateTargets(MethodHandle[] targets) {
        predicateTargets = targets;
        predicateCache = new Predicate[targets.length];
    }

    public static void installSupplierTargets(MethodHandle[] targets) {
        supplierTargets = targets;
        supplierCache = new Supplier[targets.length];
    }

    public static void installConsumerTargets(MethodHandle[] targets) {
        consumerTargets = targets;
        consumerCache = new Consumer[targets.length];
    }

    public static void installBiConsumerTargets(MethodHandle[] targets) {
        biConsumerTargets = targets;
        biConsumerCache = new BiConsumer[targets.length];
    }

    public static MethodHandle[] functionTargets() {
        return functionTargets;
    }

    public static MethodHandle[] predicateTargets() {
        return predicateTargets;
    }

    public static MethodHandle[] supplierTargets() {
        return supplierTargets;
    }

    public static MethodHandle[] consumerTargets() {
        return consumerTargets;
    }

    public static MethodHandle[] biConsumerTargets() {
        return biConsumerTargets;
    }

    public static Object invokeFunction(int id, Object arg) throws Throwable {
        return functionTargets[id].invoke(arg);
    }

    public static boolean invokePredicate(int id, Object arg) throws Throwable {
        return (boolean) predicateTargets[id].invoke(arg);
    }

    public static Object invokeSupplier(int id) throws Throwable {
        return supplierTargets[id].invoke();
    }

    public static void invokeConsumer(int id, Object arg) throws Throwable {
        consumerTargets[id].invoke(arg);
    }

    public static void invokeBiConsumer(int id, Object left, Object right) throws Throwable {
        biConsumerTargets[id].invoke(left, right);
    }

    @SuppressWarnings("unchecked")
    public static synchronized Function<Object, Object> functionAdapter(int id) {
        if (functionCache[id] == null) {
            functionCache[id] = new JmoaFunctionAdapter(id);
        }
        return (Function<Object, Object>) functionCache[id];
    }

    @SuppressWarnings("unchecked")
    public static synchronized Predicate<Object> predicateAdapter(int id) {
        if (predicateCache[id] == null) {
            predicateCache[id] = new JmoaPredicateAdapter(id);
        }
        return (Predicate<Object>) predicateCache[id];
    }

    @SuppressWarnings("unchecked")
    public static synchronized Supplier<Object> supplierAdapter(int id) {
        if (supplierCache[id] == null) {
            supplierCache[id] = new JmoaSupplierAdapter(id);
        }
        return (Supplier<Object>) supplierCache[id];
    }

    @SuppressWarnings("unchecked")
    public static synchronized Consumer<Object> consumerAdapter(int id) {
        if (consumerCache[id] == null) {
            consumerCache[id] = new JmoaConsumerAdapter(id);
        }
        return (Consumer<Object>) consumerCache[id];
    }

    @SuppressWarnings("unchecked")
    public static synchronized BiConsumer<Object, Object> biConsumerAdapter(int id) {
        if (biConsumerCache[id] == null) {
            biConsumerCache[id] = new JmoaBiConsumerAdapter(id);
        }
        return (BiConsumer<Object, Object>) biConsumerCache[id];
    }

    public static void reset() {
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
}
