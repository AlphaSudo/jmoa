package com.yourorg.jmoa.plugin.runtime;

import java.util.Arrays;
import java.util.Optional;

public enum RuntimeAdapterKind {
    FUNCTION(
        "java/util/function/Function",
        "createFunction",
        "(I)Ljava/util/function/Function;"
    ),
    PREDICATE(
        "java/util/function/Predicate",
        "createPredicate",
        "(I)Ljava/util/function/Predicate;"
    ),
    SUPPLIER(
        "java/util/function/Supplier",
        "createSupplier",
        "(I)Ljava/util/function/Supplier;"
    ),
    CONSUMER(
        "java/util/function/Consumer",
        "createConsumer",
        "(I)Ljava/util/function/Consumer;"
    ),
    BI_CONSUMER(
        "java/util/function/BiConsumer",
        "createBiConsumer",
        "(I)Ljava/util/function/BiConsumer;"
    );

    private final String samInterfaceInternalName;
    private final String factoryMethodName;
    private final String factoryMethodDescriptor;

    RuntimeAdapterKind(
        String samInterfaceInternalName,
        String factoryMethodName,
        String factoryMethodDescriptor
    ) {
        this.samInterfaceInternalName = samInterfaceInternalName;
        this.factoryMethodName = factoryMethodName;
        this.factoryMethodDescriptor = factoryMethodDescriptor;
    }

    public String samInterfaceInternalName() {
        return samInterfaceInternalName;
    }

    public String factoryMethodName() {
        return factoryMethodName;
    }

    public String factoryMethodDescriptor() {
        return factoryMethodDescriptor;
    }

    public static Optional<RuntimeAdapterKind> fromSamInterface(String samInterfaceInternalName) {
        return Arrays.stream(values())
            .filter(kind -> kind.samInterfaceInternalName.equals(samInterfaceInternalName))
            .findFirst();
    }
}
