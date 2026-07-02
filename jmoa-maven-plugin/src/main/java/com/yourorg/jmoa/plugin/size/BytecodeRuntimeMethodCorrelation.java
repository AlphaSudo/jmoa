package com.yourorg.jmoa.plugin.size;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;

public record BytecodeRuntimeMethodCorrelation(
    String className,
    String methodName,
    String descriptor,
    GeneratedClassFamily generatedFamily,
    int codeLength,
    MethodSizeRisk threshold,
    boolean staticInitializer,
    boolean synthetic,
    boolean bridge,
    boolean runtimeLoadedClass,
    long classHistogramInstances,
    long classHistogramBytes,
    RuntimeCorrelationCategory classCategory
) {
}

