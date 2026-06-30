package jmoa.runtime;

import java.util.function.Supplier;

public final class JmoaSupplierAdapter implements Supplier<Object> {

    private final int id;

    public JmoaSupplierAdapter(int id) {
        this.id = id;
    }

    @Override
    public Object get() {
        return JmoaRuntimeSupport.invokeSupplier(id);
    }
}
