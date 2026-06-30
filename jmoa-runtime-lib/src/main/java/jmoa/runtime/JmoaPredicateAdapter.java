package jmoa.runtime;

import java.util.function.Predicate;

public final class JmoaPredicateAdapter implements Predicate<Object> {

    private final int id;

    public JmoaPredicateAdapter(int id) {
        this.id = id;
    }

    @Override
    public boolean test(Object value) {
        return JmoaRuntimeSupport.invokePredicate(id, value);
    }
}
