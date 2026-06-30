package jmoa.runtime;

import java.util.function.Consumer;

public final class JmoaConsumerAdapter implements Consumer<Object> {

    private final int id;

    public JmoaConsumerAdapter(int id) {
        this.id = id;
    }

    @Override
    public void accept(Object value) {
        JmoaRuntimeSupport.invokeConsumer(id, value);
    }
}
