package com.yourorg.jmoa.plugin.measure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassLoadLogParserTest {

    @Test
    void parsesLambdaAndFrameworkCounts() {
        ClassLoadLogParser.ClassLoadMetrics metrics = new ClassLoadLogParser().parse(List.of(
            "[0.112s][info][class,load] example.MeasureMain source: file:/app/target/classes/",
            "[0.113s][info][class,load] example.MeasureMain$$Lambda/0x0000000800c00c28 source: example.MeasureMain",
            "[0.114s][info][class,load] org.springframework.context.SafeFrameworkFixture$$Lambda/0x0000000800c00d28 source: jar:file:/deps/spring.jar!/",
            "[0.114s][info][class,load] example.SomeService$JmoaPkgAdapters$SomeService$S0_deadbeef source: file:/deps/expanded/",
            "[0.114s][info][class,load] jmoa.tools.ModeCClasspathLauncher source: file:/app/tools/",
            "[0.114s][info][class,load] jmoa.runtime.JmoaFactory source: file:/app/runtime/",
            "[0.115s][info][class,load] jmoa.runtime.JmoaRuntime source: file:/app/target/classes/",
            "[0.116s][info][class,load] jmoa.runtime.JmoaRuntime$$Lambda/0x0000000800c00e28 source: jmoa.runtime.JmoaRuntime",
            "[0.117s][info][class,load] jdk.internal.classfile.ClassModel source: jrt:/java.base",
            "[0.118s][info][class,load] java.lang.classfile.ClassFile source: jrt:/java.base",
            "[0.119s][info][class,load] org.springframework.core.type.classreading.ClassFileMetadataReader source: jar:file:/deps/spring-core.jar!/",
            "[0.210s][info][class,unload] example.MeasureMain$$Lambda/0x0000000800c00c28 0x0000000800c00c28",
            "[0.211s][info][class,unload] org.springframework.context.SafeFrameworkFixture$$Lambda/0x0000000800c00d28 0x0000000800c00d28",
            "[0.212s][info][class,unload] jmoa.runtime.JmoaRuntime 0x0000000800c00f28",
            "[0.213s][info][class,unload] example.SomeService$JmoaPkgAdapters$SomeService$S0_deadbeef 0x0000000800c00f29"
        ));

        assertEquals(11, metrics.totalLoadedClasses());
        assertEquals(3, metrics.lambdaClasses());
        assertEquals(1, metrics.applicationLambdaClasses());
        assertEquals(1, metrics.frameworkLambdaClasses());
        assertEquals(1, metrics.jmoaToolClasses());
        assertEquals(2, metrics.jmoaRuntimeLibClasses());
        assertEquals(1, metrics.jmoaGeneratedOptimizationClasses());
        assertEquals(1, metrics.jmoaGeneratedPackageAdapterClasses());
        assertEquals(2, metrics.jmoaGeneratedClasses());
        assertEquals(1, metrics.jdkInternalClassfileClasses());
        assertEquals(1, metrics.javaLangClassfileClasses());
        assertEquals(1, metrics.springCoreClassReadingClasses());
        assertEquals(4, metrics.totalUnloadedClasses());
        assertEquals(2, metrics.unloadedLambdaClasses());
        assertEquals(1, metrics.unloadedApplicationLambdaClasses());
        assertEquals(1, metrics.unloadedFrameworkLambdaClasses());
        assertEquals(0, metrics.unloadedJmoaRuntimeLibClasses());
        assertEquals(1, metrics.unloadedJmoaGeneratedOptimizationClasses());
        assertEquals(1, metrics.unloadedJmoaGeneratedPackageAdapterClasses());
        assertEquals(2, metrics.unloadedJmoaGeneratedClasses());
    }
}
