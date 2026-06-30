package jmoa.runtime;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.lang.reflect.Method;

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
        private static volatile Method createFunction;
        private static volatile Method createPredicate;
        private static volatile Method createSupplier;
        private static volatile Method createConsumer;
        private static volatile Method createBiConsumer;

        private static Function<Object, Object> createFunction(int id) {
            Method method = resolveMethod("createFunction");
            if (method == null) {
                return JmoaRuntimeSupport.functionAdapter(id);
            }
            return invoke(method, id, Function.class);
        }

        private static Predicate<Object> createPredicate(int id) {
            Method method = resolveMethod("createPredicate");
            if (method == null) {
                return JmoaRuntimeSupport.predicateAdapter(id);
            }
            return invoke(method, id, Predicate.class);
        }

        private static Supplier<Object> createSupplier(int id) {
            Method method = resolveMethod("createSupplier");
            if (method == null) {
                return JmoaRuntimeSupport.supplierAdapter(id);
            }
            return invoke(method, id, Supplier.class);
        }

        private static Consumer<Object> createConsumer(int id) {
            Method method = resolveMethod("createConsumer");
            if (method == null) {
                return JmoaRuntimeSupport.consumerAdapter(id);
            }
            return invoke(method, id, Consumer.class);
        }

        private static BiConsumer<Object, Object> createBiConsumer(int id) {
            Method method = resolveMethod("createBiConsumer");
            if (method == null) {
                return JmoaRuntimeSupport.biConsumerAdapter(id);
            }
            return invoke(method, id, BiConsumer.class);
        }

        private static Method resolveMethod(String methodName) {
            ensureResolved();
            return switch (methodName) {
                case "createFunction" -> createFunction;
                case "createPredicate" -> createPredicate;
                case "createSupplier" -> createSupplier;
                case "createConsumer" -> createConsumer;
                case "createBiConsumer" -> createBiConsumer;
                default -> null;
            };
        }

        private static synchronized void ensureResolved() {
            if (resolved) {
                return;
            }
            resolved = true;
            Class<?> runtimeClass = loadGeneratedRuntimeClass();
            if (runtimeClass == null) {
                return;
            }
            try {
                createFunction = runtimeClass.getMethod("createFunction", int.class);
                createPredicate = runtimeClass.getMethod("createPredicate", int.class);
                createSupplier = runtimeClass.getMethod("createSupplier", int.class);
                createConsumer = runtimeClass.getMethod("createConsumer", int.class);
                createBiConsumer = runtimeClass.getMethod("createBiConsumer", int.class);
            } catch (ReflectiveOperationException ignored) {
                createFunction = null;
                createPredicate = null;
                createSupplier = null;
                createConsumer = null;
                createBiConsumer = null;
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

        @SuppressWarnings("unchecked")
        private static <T> T invoke(Method method, int id, Class<T> type) {
            try {
                return (T) type.cast(method.invoke(null, id));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to invoke generated runtime factory: " + method.getName(), e);
            }
        }
    }
}
