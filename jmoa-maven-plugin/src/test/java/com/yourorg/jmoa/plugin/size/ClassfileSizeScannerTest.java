package com.yourorg.jmoa.plugin.size;

import com.yourorg.jmoa.plugin.ClassRootDescriptor;
import com.yourorg.jmoa.plugin.ClassRootKind;
import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;
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
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassfileSizeScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void profilesClassfileAndLargeMethodSizeFromDirectory() throws Exception {
        Path classes = tempDir.resolve("classes");
        writeClass(classes, "com/example/LargeGenerated__BeanDefinitions",
            classWithLargeMethod("com/example/LargeGenerated__BeanDefinitions", 9_000));

        ClassfileSizeProfile profile = new ClassfileSizeScanner(BytecodeSizeConfig.defaults()).scan(
            List.of(new ClassRootDescriptor(classes.toFile(), true, ClassRootKind.PROJECT_OUTPUT)),
            List.of()
        );

        assertEquals(1, profile.totalClassesScanned());
        ClassfileSizeRecord record = profile.classes().get(0);
        assertEquals(GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS, record.generatedFamily());
        assertTrue(record.largestMethodCodeLength() >= 9_000);
        assertTrue(profile.methods().stream()
            .anyMatch(method -> method.methodName().equals("big") && method.threshold() == MethodSizeRisk.NOTICE));
        assertTrue(profile.familyBreakdown().stream()
            .anyMatch(family -> family.generatedFamily() == GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS));
    }

    @Test
    void profilesSpringBootFatJarAndNestedLibraries() throws Exception {
        Path outerJar = tempDir.resolve("app.jar");
        byte[] nestedJar = jarBytes(Map.of(
            "org/example/Foo$$SpringCGLIB$$0.class",
            simpleClass("org/example/Foo$$SpringCGLIB$$0")
        ));
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(outerJar))) {
            writeJarEntry(jar, "BOOT-INF/classes/com/example/App__BeanDefinitions.class",
                simpleClass("com/example/App__BeanDefinitions"));
            writeJarEntry(jar, "BOOT-INF/lib/proxy-fixture.jar", nestedJar);
        }

        ClassfileSizeProfile profile = new ClassfileSizeScanner(BytecodeSizeConfig.defaults()).scan(
            List.of(),
            List.of(outerJar.toFile())
        );

        assertEquals(2, profile.totalClassesScanned());
        assertTrue(profile.classes().stream()
            .anyMatch(record -> record.generatedFamily() == GeneratedClassFamily.SPRING_CGLIB
                && record.artifact().equals("proxy-fixture.jar")));
        assertTrue(profile.classes().stream()
            .anyMatch(record -> record.generatedFamily() == GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS));
    }

    @Test
    void writesBytecodeSizeReports() throws Exception {
        Path classes = tempDir.resolve("classes");
        writeClass(classes, "com/example/LambdaHolder", lambdaMetafactoryClass("com/example/LambdaHolder"));
        ClassfileSizeProfile profile = new ClassfileSizeScanner(BytecodeSizeConfig.defaults()).scan(
            List.of(new ClassRootDescriptor(classes.toFile(), true, ClassRootKind.PROJECT_OUTPUT)),
            List.of()
        );
        File out = tempDir.resolve("reports").toFile();

        new BytecodeSizeReportWriter().write(out, profile);

        assertTrue(new File(out, "classfile-size-profile.json").isFile());
        assertTrue(new File(out, "classfile-size-profile.md").isFile());
        assertTrue(new File(out, "method-code-size-report.json").isFile());
        assertTrue(new File(out, "constant-pool-bloat-report.json").isFile());
        assertTrue(new File(out, "attribute-size-report.json").isFile());
        assertTrue(new File(out, "bytecode-roi-v2-report.json").isFile());
    }

    private static byte[] simpleClass(String internalName) {
        return classBytes(internalName, writer -> {
        });
    }

    private static byte[] classWithLargeMethod(String internalName, int nopCount) {
        return classBytes(internalName, writer -> {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "big", "()V", null, null);
            method.visitCode();
            for (int i = 0; i < nopCount; i++) {
                method.visitInsn(Opcodes.NOP);
            }
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(0, 0);
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

            MethodVisitor supplier = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "supplier", "()Ljava/util/function/Supplier;", null, null);
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
            Handle implementation = new Handle(Opcodes.H_INVOKESTATIC, internalName, "lambda$supplier$0", "()Ljava/lang/String;", false);
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

    private static byte[] classBytes(String internalName, ClassBody body) {
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

    private interface ClassBody {
        void accept(ClassWriter writer);
    }
}
