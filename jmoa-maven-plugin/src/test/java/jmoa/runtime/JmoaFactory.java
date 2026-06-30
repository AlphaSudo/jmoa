package jmoa.runtime;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class JmoaFactory {

    private JmoaFactory() {
    }

    public static Function<Object, Object> createFunction(int id) {
        return new JmoaFunctionAdapter(id);
    }

    public static Predicate<Object> createPredicate(int id) {
        return new JmoaPredicateAdapter(id);
    }

    public static Supplier<Object> createSupplier(int id) {
        return new JmoaSupplierAdapter(id);
    }

    public static Consumer<Object> createConsumer(int id) {
        return new JmoaConsumerAdapter(id);
    }

    public static BiConsumer<Object, Object> createBiConsumer(int id) {
        return new JmoaBiConsumerAdapter(id);
    }
}
