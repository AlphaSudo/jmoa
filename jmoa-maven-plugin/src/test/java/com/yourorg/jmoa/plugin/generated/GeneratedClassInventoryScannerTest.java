package com.yourorg.jmoa.plugin.generated;

import com.yourorg.jmoa.plugin.ClassRootDescriptor;
import com.yourorg.jmoa.plugin.ClassRootKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedClassInventoryScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void inventoriesGeneratedFamiliesFromClassDirectory() throws Exception {
        Path classes = tempDir.resolve("classes");
        writeClass(classes, "com/example/App__BeanDefinitions", simpleClass("com/example/App__BeanDefinitions"));
        writeClass(classes, "com/example/Service$$SpringCGLIB$$0", simpleClass("com/example/Service$$SpringCGLIB$$0"));
        writeClass(classes, "com/example/LambdaHolder", lambdaMetafactoryClass("com/example/LambdaHolder"));
        writeClass(classes, "com/example/BridgeHelper", bridgeSyntheticClass("com/example/BridgeHelper"));
        writeClass(classes, "com/example/PlainService", simpleClass("com/example/PlainService"));

        GeneratedClassInventory inventory = new GeneratedClassInventoryScanner().scan(
            java.util.List.of(new ClassRootDescriptor(classes.toFile(), true, ClassRootKind.PROJECT_OUTPUT)),
            java.util.List.of()
        );

        Map<GeneratedClassFamily, Long> counts = familyCounts(inventory);
        assertEquals(5, inventory.totalClassesScanned());
        assertEquals(4, inventory.generatedLikeClasses());
        assertEquals(1L, counts.get(GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS));
        assertEquals(1L, counts.get(GeneratedClassFamily.SPRING_CGLIB));
        assertEquals(1L, counts.get(GeneratedClassFamily.LAMBDA_METAFATORY_SITE));
        assertEquals(1L, counts.get(GeneratedClassFamily.SYNTHETIC_BRIDGE_METHODS));
        assertEquals(1L, counts.get(GeneratedClassFamily.PLAIN));

        GeneratedClassRecord lambdaHolder = inventory.classes().stream()
            .filter(record -> record.className().equals("com.example.LambdaHolder"))
            .findFirst()
            .orElseThrow();
        assertEquals(1, lambdaHolder.invokedynamicCount());
        assertEquals(GeneratedClassRiskLevel.UNKNOWN, lambdaHolder.riskLevel());
        assertTrue(lambdaHolder.proxyIndicators().contains("lambda-metafactory-invokedynamic"));
    }

    @Test
    void scansSpringBootFatJarClassesAndNestedLibs() throws Exception {
        Path outerJar = tempDir.resolve("app.jar");
        byte[] nestedJar = jarBytes(Map.of(
            "org/springframework/data/FooPropertyAccessor.class",
            simpleClass("org/springframework/data/FooPropertyAccessor")
        ));
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(outerJar))) {
            writeJarEntry(jar, "BOOT-INF/classes/com/example/App__ApplicationContextInitializer.class",
                simpleClass("com/example/App__ApplicationContextInitializer"));
            writeJarEntry(jar, "BOOT-INF/lib/spring-data-fixture.jar", nestedJar);
        }

        GeneratedClassInventory inventory = new GeneratedClassInventoryScanner().scan(
            java.util.List.of(),
            java.util.List.of(outerJar.toFile())
        );

        Map<GeneratedClassFamily, Long> counts = familyCounts(inventory);
        assertEquals(2, inventory.totalClassesScanned());
        assertEquals(2, inventory.generatedLikeClasses());
        assertEquals(1L, counts.get(GeneratedClassFamily.SPRING_AOT_REGISTRATION));
        assertEquals(1L, counts.get(GeneratedClassFamily.SPRING_DATA_GENERATED));

        GeneratedClassRecord springData = inventory.classes().stream()
            .filter(record -> record.family() == GeneratedClassFamily.SPRING_DATA_GENERATED)
            .findFirst()
            .orElseThrow();
        assertEquals("spring-data-fixture.jar", springData.artifact());
        assertTrue(springData.sourceRoot().contains("BOOT-INF/lib/spring-data-fixture.jar"));
    }

    @Test
    void writesInventoryReports() throws Exception {
        Path classes = tempDir.resolve("classes");
        writeClass(classes, "com/example/App__BeanDefinitions", simpleClass("com/example/App__BeanDefinitions"));
        GeneratedClassInventory inventory = new GeneratedClassInventoryScanner().scan(
            java.util.List.of(new ClassRootDescriptor(classes.toFile(), true, ClassRootKind.PROJECT_OUTPUT)),
            java.util.List.of()
        );
        File out = tempDir.resolve("reports").toFile();

        new GeneratedClassInventoryReportWriter().write(out, inventory);

        assertTrue(new File(out, "generated-class-inventory.json").isFile());
        assertTrue(new File(out, "generated-class-inventory.md").isFile());
        assertTrue(new File(out, "generated-class-family-breakdown.json").isFile());
        assertTrue(new File(out, "generated-class-inventory-summary.csv").isFile());
        assertNotNull(Files.readString(out.toPath().resolve("generated-class-inventory.md")));
    }

    private static Map<GeneratedClassFamily, Long> familyCounts(GeneratedClassInventory inventory) {
        return inventory.classes().stream()
            .collect(Collectors.groupingBy(GeneratedClassRecord::family, Collectors.counting()));
    }

    private static byte[] simpleClass(String internalName) {
        return classBytes(internalName, ignored -> {
        });
    }

    private static byte[] bridgeSyntheticClass(String internalName) {
        return classBytes(internalName, writer -> {
            MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC,
                "get",
                "()Ljava/lang/Object;",
                null,
                null
            );
            method.visitCode();
            method.visitInsn(Opcodes.ACONST_NULL);
            method.visitInsn(Opcodes.ARETURN);
            method.visitMaxs(1, 1);
            method.visitEnd();
        });
    }

    private static byte[] lambdaMetafactoryClass(String internalName) {
        return classBytes(internalName, writer -> {
            MethodVisitor value = writer.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "lambda$supplier$0",
                "()Ljava/lang/String;",
                null,
                null
            );
            value.visitCode();
            value.visitLdcInsn("ok");
            value.visitInsn(Opcodes.ARETURN);
            value.visitMaxs(1, 0);
            value.visitEnd();

            MethodVisitor supplier = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "supplier",
                "()Ljava/util/function/Supplier;",
                null,
                null
            );
            supplier.visitCode();
            Handle bootstrap = new Handle(
                Opcodes.H_INVOKESTATIC,
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                    + "Ljava/lang/invoke/CallSite;",
                false
            );
            Handle implementation = new Handle(
                Opcodes.H_INVOKESTATIC,
                internalName,
                "lambda$supplier$0",
                "()Ljava/lang/String;",
                false
            );
            supplier.visitInvokeDynamicInsn(
                "get",
                "()Ljava/util/function/Supplier;",
                bootstrap,
                Type.getType("()Ljava/lang/Object;"),
                implementation,
                Type.getType("()Ljava/lang/String;")
            );
            supplier.visitInsn(Opcodes.ARETURN);
            supplier.visitMaxs(1, 0);
            supplier.visitEnd();
        });
    }

    private static byte[] classBytes(String internalName, Consumer<ClassWriter> body) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
        body.accept(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeClass(Path root, String internalName, byte[] bytes) throws IOException {
        Path target = root.resolve(internalName + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }

    private static byte[] jarBytes(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                writeJarEntry(jar, entry.getKey(), entry.getValue());
            }
        }
        return out.toByteArray();
    }

    private static void writeJarEntry(JarOutputStream jar, String name, byte[] bytes) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(bytes);
        jar.closeEntry();
    }
}
