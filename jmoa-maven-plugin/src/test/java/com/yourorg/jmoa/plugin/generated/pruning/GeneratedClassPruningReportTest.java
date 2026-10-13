package com.yourorg.jmoa.plugin.generated.pruning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedClassPruningReportTest {

    @TempDir Path tempDir;

    @Test
    void structuredCodeResourcesAndReflectionAllowlistsBecomeRoots() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("classes"));
        String methodTarget = "jmoa/runtime/JmoaDirectAdapters$MethodTarget";
        String bootstrapTarget = "jmoa/runtime/JmoaDirectAdapters$BootstrapTarget";
        String serviceTarget = "jmoa/runtime/JmoaDirectAdapters$ServiceTarget";
        String reflectionTarget = "jmoa/runtime/JmoaDirectAdapters$ReflectTarget";
        String stale = "jmoa/runtime/JmoaDirectAdapters$Stale";
        for (String generated : List.of(methodTarget, bootstrapTarget, serviceTarget, reflectionTarget, stale)) {
            writeEmptyClass(root, generated);
        }
        writeStructuredCaller(root, "app/Main", methodTarget, bootstrapTarget);
        Path service = root.resolve("META-INF/services/example.Api");
        Files.createDirectories(service.getParent());
        Files.writeString(service, serviceTarget.replace('/', '.'));
        Path allowlist = tempDir.resolve("configured-reflection.txt");
        Files.writeString(allowlist, reflectionTarget.replace('/', '.'));

        GeneratedClassPruningReport.PruningManifest report = GeneratedClassPruningReport.build(
                List.of(root.toFile()), Set.of("app/Main"), List.of(allowlist.toFile()),
                Set.of(methodTarget, bootstrapTarget, serviceTarget, reflectionTarget, stale));

        assertFalse(report.deletionEnabled());
        assertEquals("jmoa-generated-class-pruning-report-v3", report.schemaVersion());
        assertFalse(entry(report, methodTarget).removable());
        assertFalse(entry(report, bootstrapTarget).removable());
        assertFalse(entry(report, serviceTarget).removable());
        assertFalse(entry(report, reflectionTarget).removable());
        assertTrue(entry(report, stale).removable());
        assertTrue(report.referenceEvidence().get(methodTarget).structuredClassReferrers().contains("app/Main"));
        assertTrue(report.referenceEvidence().get(bootstrapTarget).structuredClassReferrers().contains("app/Main"));
        assertTrue(report.referenceEvidence().get(serviceTarget).resourceReferrers().stream()
                .anyMatch(path -> path.endsWith("example.Api")));
        assertTrue(report.configuredReflectionAllowlists().contains(allowlist.toAbsolutePath().toString()));
        assertTrue(report.entries().stream().allMatch(value -> !value.origins().isEmpty()));
        assertTrue(report.safetyReviewPassed());
        assertTrue(report.missingDeclaredGeneratedClasses().isEmpty());
        assertTrue(report.unaccountedGeneratedClasses().isEmpty());
        assertTrue(report.danglingGeneratedReferences().isEmpty());
    }

    @Test
    void exactPackageLocalGeneratorOutputIsInventoriedWithoutBecomingARoot() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("package-local-classes"));
        String referenced = "com/example/service/J$PackageSignature$Referenced";
        String stale = "com/example/service/J$PackageSignature$Stale";
        writeEmptyClass(root, referenced);
        writeEmptyClass(root, stale);
        writeStructuredCaller(root, "app/Caller", referenced, referenced);

        GeneratedClassPruningReport.PruningManifest report = GeneratedClassPruningReport.build(
                List.of(root.toFile()),
                Set.of(referenced),
                List.of(),
                Set.of(referenced, stale));

        assertEquals(2, report.generatedClassCount(), "both exact package-local generator outputs must be inventoried");
        assertFalse(entry(report, referenced).removable());
        assertEquals("KEEP_EXPLICIT_RUNTIME_ENTRY_POINT", entry(report, referenced).reason());
        assertTrue(entry(report, stale).removable(),
                "known-generated is inventory metadata and must not accidentally root an unreferenced class");
        assertEquals("REMOVE_GENERATED_ORPHAN", entry(report, stale).reason());
        assertTrue(report.referenceEvidence().get(referenced).structuredClassReferrers().contains("app/Caller"));
        assertTrue(report.safetyReviewPassed());
    }

    @Test
    void missingDeclaredGeneratorOutputFailsTheSafetyReviewAndReportsDanglingUse() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("dangling-classes"));
        String missing = "com/example/service/J$PackageSignature$Missing";
        writeStructuredCaller(root, "app/DanglingCaller", missing, missing);

        GeneratedClassPruningReport.PruningManifest report = GeneratedClassPruningReport.build(
                List.of(root.toFile()), Set.of(missing), List.of(), Set.of(missing));

        assertFalse(report.safetyReviewPassed());
        assertEquals(List.of(missing), report.missingDeclaredGeneratedClasses());
        assertTrue(report.danglingGeneratedReferences().contains("class:app/DanglingCaller->" + missing));
    }

    @Test
    void indexedUtf8FallbackPreservesInternalAndDottedSubstringSemantics() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("utf8-fallback-classes"));
        String internalTarget = "com/example/service/J$R12_AABBCCDDEEFF001122334455";
        String dottedTarget = "com/example/service/J$R13_00112233445566778899AABB";
        writeEmptyClass(root, internalTarget);
        writeEmptyClass(root, dottedTarget);
        writeUtf8Caller(root, "app/ReflectiveCaller",
                "prefix:" + internalTarget + ":suffix",
                "prefix:" + dottedTarget.replace('/', '.') + ":suffix");

        GeneratedClassPruningReport.PruningManifest report = GeneratedClassPruningReport.build(
                List.of(root.toFile()), Set.of("app/ReflectiveCaller"), List.of(),
                Set.of(internalTarget, dottedTarget));

        assertTrue(report.referenceEvidence().get(internalTarget).conservativeUtf8Referrers()
                .contains("app/ReflectiveCaller"));
        assertTrue(report.referenceEvidence().get(dottedTarget).conservativeUtf8Referrers()
                .contains("app/ReflectiveCaller"));
        assertFalse(entry(report, internalTarget).removable());
        assertFalse(entry(report, dottedTarget).removable());
    }

    private static GeneratedClassPruningReport.ManifestEntry entry(
            GeneratedClassPruningReport.PruningManifest report, String name) {
        return report.entries().stream().filter(value -> value.internalName().equals(name)).findFirst().orElseThrow();
    }

    private static void writeEmptyClass(Path root, String internalName) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        writer.visitEnd();
        write(root, internalName, writer.toByteArray());
    }

    private static void writeStructuredCaller(Path root, String internalName, String methodTarget, String bootstrapTarget)
            throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "()V", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, methodTarget, "m", "()V", false);
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, bootstrapTarget, "bootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false);
        method.visitInvokeDynamicInsn("run", "()Ljava/lang/Runnable;", bootstrap, Type.getObjectType(methodTarget));
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        write(root, internalName, writer.toByteArray());
    }

    private static void writeUtf8Caller(Path root, String internalName, String... values) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "strings", "()V", null, null);
        method.visitCode();
        for (String value : values) {
            method.visitLdcInsn(value);
            method.visitInsn(Opcodes.POP);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        write(root, internalName, writer.toByteArray());
    }

    private static void write(Path root, String internalName, byte[] bytes) throws Exception {
        Path path = root.resolve(internalName + ".class");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }
}
