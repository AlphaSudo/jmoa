package jmoa.runtime;

import java.lang.invoke.MethodHandle;

public final class JmoaRuntimeSupport {

    private static MethodHandle[] functionTargets = new MethodHandle[0];
    private static MethodHandle[] predicateTargets = new MethodHandle[0];
    private static MethodHandle[] supplierTargets = new MethodHandle[0];
    private static MethodHandle[] consumerTargets = new MethodHandle[0];
    private static MethodHandle[] biConsumerTargets = new MethodHandle[0];

    private JmoaRuntimeSupport() {
    }

    public static void installFunctionTargets(MethodHandle[] targets) {
        functionTargets = targets;
    }

    public static void installPredicateTargets(MethodHandle[] targets) {
        predicateTargets = targets;
    }

    public static void installSupplierTargets(MethodHandle[] targets) {
        supplierTargets = targets;
    }

    public static void installConsumerTargets(MethodHandle[] targets) {
        consumerTargets = targets;
    }

    public static void installBiConsumerTargets(MethodHandle[] targets) {
        biConsumerTargets = targets;
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

    public static void reset() {
        functionTargets = new MethodHandle[0];
        predicateTargets = new MethodHandle[0];
        supplierTargets = new MethodHandle[0];
        consumerTargets = new MethodHandle[0];
        biConsumerTargets = new MethodHandle[0];
    }
}
