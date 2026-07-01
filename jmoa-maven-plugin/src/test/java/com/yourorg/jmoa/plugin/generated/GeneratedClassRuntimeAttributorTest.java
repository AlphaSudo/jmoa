package com.yourorg.jmoa.plugin.generated;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedClassRuntimeAttributorTest {

    @TempDir
    Path tempDir;

    @Test
    void attributesRuntimeOnlyGeneratedClassesFromClassLoadAndHistogram() throws Exception {
        GeneratedClassInventory inventory = new GeneratedClassInventory(
            "test",
            "now",
            1,
            1,
            128,
            List.of(new GeneratedClassFamilySummary(
                GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS,
                1,
                1,
                128,
                2,
                0,
                0,
                0,
                0,
                java.util.Map.of("application", 1L)
            )),
            List.of(new GeneratedClassRecord(
                "com.example.App__BeanDefinitions",
                "com/example/App__BeanDefinitions",
                "target/classes",
                "com/example/App__BeanDefinitions.class",
                "application",
                GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS,
                List.of(),
                2,
                0,
                0,
                0,
                128,
                20,
                0,
                List.of("spring-aot-bean-definitions-pattern"),
                GeneratedClassRiskLevel.UNKNOWN,
                null,
                true
            ))
        );
        Path classLoadLog = tempDir.resolve("classload.log");
        Files.writeString(classLoadLog, """
            [0.001s][info][class,load] com.example.App__BeanDefinitions source: file:/app/BOOT-INF/classes/
            [0.002s][info][class,load] com.example.Service$$SpringCGLIB$$0 source: __JVM_DefineClass__
            [0.003s][info][class,load] com.example.Foo$$Lambda/0x0000000800c01000 source: com.example.Foo
            """);
        Path histogram = tempDir.resolve("histogram.txt");
        Files.writeString(histogram, """
             num     #instances         #bytes  class name (module)
               1:             3             96  com.example.Service$$SpringCGLIB$$0
               2:             5            160  com.example.App__BeanDefinitions
            """);

        GeneratedClassRuntimeAttribution attribution = new GeneratedClassRuntimeAttributor()
            .attribute(inventory, classLoadLog.toFile(), histogram.toFile());

        assertEquals(3, attribution.totalRuntimeLoadedClasses());
        assertEquals(3, attribution.totalGeneratedRuntimeLoadedClasses());
        assertTrue(attribution.families().stream()
            .anyMatch(family -> family.family() == GeneratedClassFamily.SPRING_CGLIB
                && family.runtimeOnlyLoadedCount() == 1
                && family.histogramBytes() == 96));
        GeneratedClassRuntimeClassRecord aot = attribution.classes().stream()
            .filter(record -> record.className().equals("com.example.App__BeanDefinitions"))
            .findFirst()
            .orElseThrow();
        assertTrue(aot.staticInventoryPresent());
        assertEquals("HAS_LIVE_INSTANCES", aot.survival());
        assertNotNull(aot.loadOrigin());
    }
}
