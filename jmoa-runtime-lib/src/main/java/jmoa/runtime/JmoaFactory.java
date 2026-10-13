package jmoa.runtime;

import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class JmoaFactory {

    private JmoaFactory() {
    }

    public static Function<Object, Object> createFunction(int id) {
        return GeneratedRuntimeBridge.createFunction(id);
    }

    public static Predicate<Object> createPredicate(int id) {
        return GeneratedRuntimeBridge.createPredicate(id);
    }

    public static Supplier<Object> createSupplier(int id) {
        return GeneratedRuntimeBridge.createSupplier(id);
    }

    public static Consumer<Object> createConsumer(int id) {
        return GeneratedRuntimeBridge.createConsumer(id);
    }

    public static BiConsumer<Object, Object> createBiConsumer(int id) {
        return GeneratedRuntimeBridge.createBiConsumer(id);
    }

    private static final class GeneratedRuntimeBridge {
        private static volatile boolean resolved;
        private static boolean lazyAdapters;
        private static Method createFunction;
        private static Method createPredicate;
        private static Method createSupplier;
        private static Method createConsumer;
        private static Method createBiConsumer;

        @SuppressWarnings("unchecked")
        private static Function<Object, Object> createFunction(int id) {
            ensureResolved();
            return lazyAdapters || createFunction == null
                ? JmoaRuntimeSupport.functionAdapter(id)
                : (Function<Object, Object>) invoke(createFunction, id);
        }

        @SuppressWarnings("unchecked")
        private static Predicate<Object> createPredicate(int id) {
            ensureResolved();
            return lazyAdapters || createPredicate == null
                ? JmoaRuntimeSupport.predicateAdapter(id)
                : (Predicate<Object>) invoke(createPredicate, id);
        }

        @SuppressWarnings("unchecked")
        private static Supplier<Object> createSupplier(int id) {
            ensureResolved();
            return lazyAdapters || createSupplier == null
                ? JmoaRuntimeSupport.supplierAdapter(id)
                : (Supplier<Object>) invoke(createSupplier, id);
        }

        @SuppressWarnings("unchecked")
        private static Consumer<Object> createConsumer(int id) {
            ensureResolved();
            return lazyAdapters || createConsumer == null
                ? JmoaRuntimeSupport.consumerAdapter(id)
                : (Consumer<Object>) invoke(createConsumer, id);
        }

        @SuppressWarnings("unchecked")
        private static BiConsumer<Object, Object> createBiConsumer(int id) {
            ensureResolved();
            return lazyAdapters || createBiConsumer == null
                ? JmoaRuntimeSupport.biConsumerAdapter(id)
                : (BiConsumer<Object, Object>) invoke(createBiConsumer, id);
        }

        private static synchronized void ensureResolved() {
            if (resolved) {
                return;
            }
            Class<?> runtimeClass = loadGeneratedRuntimeClass();
            if (runtimeClass != null) {
                lazyAdapters = hasLazyAdapters(runtimeClass);
                if (!lazyAdapters) {
                    createFunction = resolveMethod(runtimeClass, "createFunction");
                    createPredicate = resolveMethod(runtimeClass, "createPredicate");
                    createSupplier = resolveMethod(runtimeClass, "createSupplier");
                    createConsumer = resolveMethod(runtimeClass, "createConsumer");
                    createBiConsumer = resolveMethod(runtimeClass, "createBiConsumer");
                }
            }
            resolved = true;
        }

        private static boolean hasLazyAdapters(Class<?> runtimeClass) {
            try {
                return Boolean.TRUE.equals(runtimeClass.getMethod("usesLazyAdapters").invoke(null));
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }

        private static Method resolveMethod(Class<?> runtimeClass, String methodName) {
            try {
                return runtimeClass.getMethod(methodName, int.class);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        private static Object invoke(Method method, int id) {
            try {
                return method.invoke(null, id);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Generated JMOA runtime factory invocation failed", e);
            }
        }

        private static Class<?> loadGeneratedRuntimeClass() {
            for (ClassLoader loader : candidateLoaders()) {
                if (loader == null) {
                    continue;
                }
                try {
                    return Class.forName("jmoa.runtime.JmoaRuntime", true, loader);
                } catch (ClassNotFoundException ignored) {
                }
            }
            return null;
        }

        private static ClassLoader[] candidateLoaders() {
            return new ClassLoader[]{
                Thread.currentThread().getContextClassLoader(),
                JmoaFactory.class.getClassLoader()
            };
        }

    }
}
