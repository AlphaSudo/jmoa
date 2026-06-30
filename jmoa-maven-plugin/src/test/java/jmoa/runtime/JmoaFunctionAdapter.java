package jmoa.runtime;

import java.util.function.Function;

public final class JmoaFunctionAdapter implements Function<Object, Object> {

    private final int id;

    public JmoaFunctionAdapter(int id) {
        this.id = id;
    }

    @Override
    public Object apply(Object value) {
        try {
            return JmoaRuntimeSupport.invokeFunction(id, value);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
