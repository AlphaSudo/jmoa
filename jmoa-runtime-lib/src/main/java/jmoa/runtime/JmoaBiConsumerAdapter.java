package jmoa.runtime;

import java.util.function.BiConsumer;

public final class JmoaBiConsumerAdapter implements BiConsumer<Object, Object> {

    private final int id;

    public JmoaBiConsumerAdapter(int id) {
        this.id = id;
    }

    @Override
    public void accept(Object left, Object right) {
        JmoaRuntimeSupport.invokeBiConsumer(id, left, right);
    }
}
