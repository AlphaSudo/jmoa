package com.yourorg.jmoa.plugin.reducer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalVariableDebugAttributeReducerTest {

    @TempDir
    Path tempDir;

    @Test
    void stripsOnlyLocalVariableDebugAttributes() {
        byte[] original = localVariableFixture();
        ClassDebugMetadataInspector inspector = new ClassDebugMetadataInspector();
        ClassDebugMetadata before = inspector.inspect(original);

        byte[] reduced = new LocalVariableDebugAttributeReducer().reduce(original);
        ClassDebugMetadata after = inspector.inspect(reduced);

        assertTrue(before.localVariableTableBytes() > 0);
        assertTrue(before.localVariableTypeTableBytes() > 0);
        assertTrue(before.lineNumberTableBytes() > 0);
        assertTrue(before.annotationAttributeBytes() > 0);
        assertTrue(before.signatureAttributeBytes() > 0);
        assertTrue(before.bootstrapMethodsAttributeBytes() > 0);

        assertEquals(0, after.localVariableTableBytes());
        assertEquals(0, after.localVariableTypeTableBytes());
        assertEquals(before.lineNumberTableBytes(), after.lineNumberTableBytes());
        assertEquals(before.annotationAttributeBytes(), after.annotationAttributeBytes());
        assertEquals(before.signatureAttributeBytes(), after.signatureAttributeBytes());
        assertEquals(before.bootstrapMethodsAttributeBytes(), after.bootstrapMethodsAttributeBytes());
    }

    @Test
    void estimatesSavingsWithoutMutation() throws Exception {
        Path input = tempDir.resolve("input");
        Files.createDirectories(input);
        Path jar = input.resolve("fixture.jar");
        writeJar(jar, "com/example/DebugFixture.class", localVariableFixture());

        ReducerConfig config = new ReducerConfig(
            true,
            false,
            "none",
            input.toFile(),
            tempDir.resolve("out").toFile(),
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false
        );

        ReducerReport report = new DebugMetadataSavingsEstimator(config).estimate();

        assertEquals(1, report.jarCount());
        assertEquals(1, report.classCount());
        assertTrue(report.totalEstimatedRemovableBytes() > 0);
        assertEquals(0, report.totalRemovedBytes());
    }

    @Test
    void reducesJarOnlyWhenExplicitReleaseLowFootprintGateIsOpen() throws Exception {
        Path input = tempDir.resolve("input");
        Path output = tempDir.resolve("out");
        Files.createDirectories(input);
        Path jar = input.resolve("fixture.jar");
        writeJar(jar, "com/example/DebugFixture.class", localVariableFixtureWithoutBootstrap());

        ReducerConfig config = new ReducerConfig(
            false,
            true,
            "release-low-footprint",
            input.toFile(),
            output.toFile(),
            true,
            true,
            false,
            false,
            false,
            false,
            false,
            false
        );
        new ReducerSafetyPolicy().validate(config);

        ReducerReport report = new JarReducer(config).reduce();

        assertTrue(report.mutationEnabled());
        assertEquals(1, report.jarCount());
        assertTrue(report.totalRemovedBytes() >= 0);
        File reduced = output.resolve("fixture.jar").toFile();
        assertTrue(reduced.isFile());
        try (JarFile reducedJar = new JarFile(reduced)) {
            byte[] bytes = reducedJar.getInputStream(reducedJar.getEntry("com/example/DebugFixture.class")).readAllBytes();
            ClassDebugMetadata after = new ClassDebugMetadataInspector().inspect(bytes);
            assertEquals(0, after.localVariableTableBytes());
            assertEquals(0, after.localVariableTypeTableBytes());
            assertTrue(after.lineNumberTableBytes() > 0);
            assertTrue(after.annotationAttributeBytes() > 0);
        }
    }

    @Test
    void jarReducerSkipsClassesWithBootstrapMethods() throws Exception {
        Path input = tempDir.resolve("input-bootstrap");
        Path output = tempDir.resolve("out-bootstrap");
        Files.createDirectories(input);
        Path jar = input.resolve("fixture.jar");
        writeJar(jar, "com/example/DebugFixture.class", localVariableFixture());
        ReducerConfig config = new ReducerConfig(
            false,
            true,
            "release-low-footprint",
            input.toFile(),
            output.toFile(),
            true,
            true,
            false,
            false,
            false,
            false,
            false,
            false
        );

        ReducerReport report = new JarReducer(config).reduce();

        assertEquals("SKIPPED_BOOTSTRAP_METHODS", report.artifacts().get(0).classes().get(0).status());
    }

    @Test
    void jarReducerSkipsSignedJarsByDefault() throws Exception {
        Path input = tempDir.resolve("input-signed");
        Path output = tempDir.resolve("out-signed");
        Files.createDirectories(input);
        Path jar = input.resolve("signed.jar");
        writeJar(jar, "com/example/DebugFixture.class", localVariableFixtureWithoutBootstrap(),
            "META-INF/TEST.SF", "Signature-Version: 1.0\n");
        ReducerConfig config = releaseReducerConfig(input, output);

        ReducerReport report = new JarReducer(config).reduce();
        JarReductionRecord artifact = report.artifacts().get(0);

        assertEquals("SKIPPED_SIGNED_JAR", artifact.status());
        assertTrue(artifact.signedJar());
        assertEquals(0, artifact.reducedClassCount());
        assertEquals(artifact.inputSha256(), artifact.outputSha256());
        assertTrue(output.resolve("signed.jar").toFile().isFile());
    }

    @Test
    void jarReducerSkipsMultiReleaseJarsByDefault() throws Exception {
        Path input = tempDir.resolve("input-mr");
        Path output = tempDir.resolve("out-mr");
        Files.createDirectories(input);
        Path jar = input.resolve("multi-release.jar");
        writeJar(jar, "com/example/DebugFixture.class", localVariableFixtureWithoutBootstrap(),
            "META-INF/versions/17/com/example/DebugFixture.class", localVariableFixtureWithoutBootstrap());
        ReducerConfig config = releaseReducerConfig(input, output);

        ReducerReport report = new JarReducer(config).reduce();
        JarReductionRecord artifact = report.artifacts().get(0);

        assertEquals("SKIPPED_MULTI_RELEASE_JAR", artifact.status());
        assertTrue(artifact.multiReleaseJar());
        assertEquals(0, artifact.reducedClassCount());
        assertEquals(artifact.inputSha256(), artifact.outputSha256());
    }

    @Test
    void jarReducerSkipsSealedJarsByDefault() throws Exception {
        Path input = tempDir.resolve("input-sealed");
        Path output = tempDir.resolve("out-sealed");
        Files.createDirectories(input);
        Path jar = input.resolve("sealed.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Sealed", "true");
        writeJar(jar, manifest, "com/example/DebugFixture.class", localVariableFixtureWithoutBootstrap());
        ReducerConfig config = releaseReducerConfig(input, output);

        ReducerReport report = new JarReducer(config).reduce();
        JarReductionRecord artifact = report.artifacts().get(0);

        assertEquals("SKIPPED_SEALED_JAR", artifact.status());
        assertTrue(artifact.sealedJar());
        assertEquals(0, artifact.reducedClassCount());
        assertEquals(artifact.inputSha256(), artifact.outputSha256());
    }

    @Test
    void jarReducerPreservesModuleInfoClass() throws Exception {
        Path input = tempDir.resolve("input-module");
        Path output = tempDir.resolve("out-module");
        Files.createDirectories(input);
        Path jar = input.resolve("module.jar");
        writeJar(jar, "module-info.class", localVariableFixtureWithoutBootstrap());
        ReducerConfig config = releaseReducerConfig(input, output);

        ReducerReport report = new JarReducer(config).reduce();

        assertEquals("SKIPPED_MODULE_INFO", report.artifacts().get(0).classes().get(0).status());
        try (JarFile reducedJar = new JarFile(output.resolve("module.jar").toFile())) {
            byte[] bytes = reducedJar.getInputStream(reducedJar.getEntry("module-info.class")).readAllBytes();
            ClassDebugMetadata after = new ClassDebugMetadataInspector().inspect(bytes);
            assertTrue(after.localVariableTableBytes() > 0);
            assertTrue(after.localVariableTypeTableBytes() > 0);
        }
    }

    @Test
    void unsafeFlagsFailFast() {
        ReducerConfig config = new ReducerConfig(
            true,
            false,
            "none",
            tempDir.toFile(),
            tempDir.resolve("out").toFile(),
            false,
            false,
            false,
            false,
            true,
            false,
            false,
            false
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new ReducerSafetyPolicy().validate(config));
        assertTrue(error.getMessage().contains("UNSAFE_REDUCER_NOT_IMPLEMENTED"));
    }

    @Test
    void writesReducerReports() throws Exception {
        Path input = tempDir.resolve("input");
        Path output = tempDir.resolve("reports");
        Files.createDirectories(input);
        writeJar(input.resolve("fixture.jar"), "com/example/DebugFixture.class", localVariableFixture());
        ReducerConfig config = new ReducerConfig(
            true,
            false,
            "none",
            input.toFile(),
            output.toFile(),
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false
        );
        ReducerReport report = new DebugMetadataSavingsEstimator(config).estimate();

        new ReducerReportWriter().write(output.toFile(), report);

        assertTrue(output.resolve("reducer-build-report.json").toFile().isFile());
        assertTrue(output.resolve("reducer-build-report.md").toFile().isFile());
        assertTrue(output.resolve("debug-metadata-savings-estimate.json").toFile().isFile());
        assertTrue(output.resolve("bytecode-reducer-safety-taxonomy.json").toFile().isFile());
    }

    private static byte[] localVariableFixture() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/DebugFixture",
            "<T:Ljava/lang/Object;>Ljava/lang/Object;",
            "java/lang/Object",
            null
        );
        writer.visitAnnotation("Ljava/lang/Deprecated;", true).visitEnd();
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        MethodVisitor lambda = writer.visitMethod(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
            "lambda$value$0",
            "()Ljava/lang/String;",
            null,
            null
        );
        lambda.visitCode();
        lambda.visitLdcInsn("ok");
        lambda.visitInsn(Opcodes.ARETURN);
        lambda.visitMaxs(1, 0);
        lambda.visitEnd();

        MethodVisitor method = writer.visitMethod(
            Opcodes.ACC_PUBLIC,
            "value",
            "(Ljava/util/List;)Ljava/util/function/Supplier;",
            "(Ljava/util/List<Ljava/lang/String;>;)Ljava/util/function/Supplier<Ljava/lang/String;>;",
            null
        );
        method.visitCode();
        Label start = new Label();
        Label end = new Label();
        method.visitLabel(start);
        method.visitLineNumber(42, start);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true);
        Label notEmpty = new Label();
        method.visitJumpInsn(Opcodes.IFEQ, notEmpty);
        method.visitLabel(notEmpty);
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
            "com/example/DebugFixture",
            "lambda$value$0",
            "()Ljava/lang/String;",
            false
        );
        method.visitInvokeDynamicInsn(
            "get",
            "()Ljava/util/function/Supplier;",
            bootstrap,
            Type.getType("()Ljava/lang/Object;"),
            implementation,
            Type.getType("()Ljava/lang/String;")
        );
        method.visitLabel(end);
        method.visitInsn(Opcodes.ARETURN);
        method.visitLocalVariable("this", "Lcom/example/DebugFixture;", null, start, end, 0);
        method.visitLocalVariable("items", "Ljava/util/List;", "Ljava/util/List<Ljava/lang/String;>;", start, end, 1);
        method.visitMaxs(1, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] localVariableFixtureWithoutBootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/DebugFixture",
            "<T:Ljava/lang/Object;>Ljava/lang/Object;",
            "java/lang/Object",
            null
        );
        writer.visitAnnotation("Ljava/lang/Deprecated;", true).visitEnd();
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        MethodVisitor method = writer.visitMethod(
            Opcodes.ACC_PUBLIC,
            "value",
            "(Ljava/util/List;)Ljava/lang/String;",
            "(Ljava/util/List<Ljava/lang/String;>;)Ljava/lang/String;",
            null
        );
        method.visitCode();
        Label start = new Label();
        Label end = new Label();
        method.visitLabel(start);
        method.visitLineNumber(42, start);
        method.visitLdcInsn("ok");
        method.visitLabel(end);
        method.visitInsn(Opcodes.ARETURN);
        method.visitLocalVariable("this", "Lcom/example/DebugFixture;", null, start, end, 0);
        method.visitLocalVariable("items", "Ljava/util/List;", "Ljava/util/List<Ljava/lang/String;>;", start, end, 1);
        method.visitMaxs(1, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeJar(Path jarPath, String entryName, byte[] classBytes) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            JarEntry entry = new JarEntry(entryName);
            jar.putNextEntry(entry);
            jar.write(classBytes);
            jar.closeEntry();
        }
    }

    private static void writeJar(Path jarPath, String entryName, byte[] classBytes, String extraEntry, String extraText)
        throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            JarEntry entry = new JarEntry(entryName);
            jar.putNextEntry(entry);
            jar.write(classBytes);
            jar.closeEntry();
            JarEntry extra = new JarEntry(extraEntry);
            jar.putNextEntry(extra);
            jar.write(extraText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    private static void writeJar(Path jarPath, String entryName, byte[] classBytes, String extraEntry, byte[] extraBytes)
        throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            JarEntry entry = new JarEntry(entryName);
            jar.putNextEntry(entry);
            jar.write(classBytes);
            jar.closeEntry();
            JarEntry extra = new JarEntry(extraEntry);
            jar.putNextEntry(extra);
            jar.write(extraBytes);
            jar.closeEntry();
        }
    }

    private static void writeJar(Path jarPath, Manifest manifest, String entryName, byte[] classBytes) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            JarEntry entry = new JarEntry(entryName);
            jar.putNextEntry(entry);
            jar.write(classBytes);
            jar.closeEntry();
        }
    }

    private static ReducerConfig releaseReducerConfig(Path input, Path output) {
        return new ReducerConfig(
            false,
            true,
            "release-low-footprint",
            input.toFile(),
            output.toFile(),
            true,
            true,
            false,
            false,
            false,
            false,
            false,
            false
        );
    }
}
