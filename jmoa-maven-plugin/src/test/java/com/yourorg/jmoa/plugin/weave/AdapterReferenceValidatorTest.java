package com.yourorg.jmoa.plugin.weave;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdapterReferenceValidatorTest {

    private static final String ADAPTER_INTERNAL = "org/springframework/security/config/annotation/method/configuration/JmoaPkgAdapters$Supplier";

    /**
     * Reproduces the Phase 32K-F1 defect scenario: a rewritten class that
     * references a JmoaPkgAdapters class which is absent from its root must be
     * reported as a missing reference.
     */
    @Test
    void detectsMissingAdapterReferenceInRoot() throws IOException {
        Path root = Files.createTempDirectory("jmoa-validator-missing");
        // Write a class whose constant pool references the adapter.
        writeRewrittenClass(root, "org/springframework/security/config/annotation/method/configuration/MethodObservationConfiguration$1");
        // Do NOT write the adapter class file.

        AdapterReferenceValidator validator = new AdapterReferenceValidator(silentLog());
        AdapterReferenceValidator.Result result = validator.validate(Arrays.asList(root.toFile()));

        assertEquals(1, result.roots().size());
        assertFalse(result.allRootsClean(), "validator must flag the missing adapter reference");
        assertEquals(1, result.missingReferences().size());
        assertEquals(ADAPTER_INTERNAL, result.missingReferences().get(0).referencedAdapterInternal());
        assertEquals(1, result.roots().get(0).missing().get(0).referencingClassBinaryNames().size());
    }

    /**
     * When the adapter IS present in the same root as the referencing class,
     * the root is clean.
     */
    @Test
    void passesWhenAdapterPresentInSameRoot() throws IOException {
        Path root = Files.createTempDirectory("jmoa-validator-present");
        writeRewrittenClass(root, "org/springframework/security/config/annotation/method/configuration/MethodObservationConfiguration$1");
        writeAdapterClass(root, ADAPTER_INTERNAL);

        AdapterReferenceValidator validator = new AdapterReferenceValidator(silentLog());
        AdapterReferenceValidator.Result result = validator.validate(Arrays.asList(root.toFile()));

        assertTrue(result.allRootsClean(), "root with both referencing class and adapter must be clean");
        assertEquals(0, result.missingReferences().size());
    }

    /**
     * Two roots: rootA has the rewritten class + adapter (clean), rootB has the
     * rewritten class but NO adapter (the exact Phase 32K-F1 multi-root defect).
     * Validator must flag rootB only.
     */
    @Test
    void flagsOnlyTheRootMissingTheAdapterInMultiRootScenario() throws IOException {
        Path rootA = Files.createTempDirectory("jmoa-multi-rootA");
        Path rootB = Files.createTempDirectory("jmoa-multi-rootB");
        writeRewrittenClass(rootA, "org/springframework/security/config/annotation/method/configuration/MethodObservationConfiguration$1");
        writeAdapterClass(rootA, ADAPTER_INTERNAL);
        writeRewrittenClass(rootB, "org/springframework/security/config/annotation/method/configuration/MethodObservationConfiguration$2");

        AdapterReferenceValidator validator = new AdapterReferenceValidator(silentLog());
        AdapterReferenceValidator.Result result = validator.validate(Arrays.asList(rootA.toFile(), rootB.toFile()));

        assertFalse(result.allRootsClean());
        assertEquals(1, result.missingReferences().size(), "only rootB should be flagged");
        // rootA report must be clean, rootB report must have one missing.
        assertTrue(result.roots().get(0).clean(), "rootA clean");
        assertFalse(result.roots().get(1).clean(), "rootB not clean");
        assertEquals(1, result.roots().get(1).missing().size());
    }

    /**
     * A class that references no adapters must produce no references and be clean.
     */
    @Test
    void ignoresClassesWithoutAdapterReferences() throws IOException {
        Path root = Files.createTempDirectory("jmoa-validator-none");
        writePlainClass(root, "com/example/Plain");

        AdapterReferenceValidator validator = new AdapterReferenceValidator(silentLog());
        AdapterReferenceValidator.Result result = validator.validate(Arrays.asList(root.toFile()));

        assertTrue(result.allRootsClean());
        assertEquals(0, result.roots().get(0).referencedAdapters().size());
        assertEquals(0, result.roots().get(0).presentAdapters().size());
    }

    // ---- helpers to synthesize minimal class files with a constant-pool Class entry ----

    /**
     * Writes a minimal valid class that has a {@code Class} constant-pool entry
     * pointing at the adapter internal name. The class declares a static field
     * typed by the adapter so the constant-pool Class entry is emitted.
     */
    private static void writeRewrittenClass(Path root, String internalName) throws IOException {
        byte[] bytes = classWithAdapterFieldReference(internalName, ADAPTER_INTERNAL);
        writeFile(root, internalName + ".class", bytes);
    }

    private static void writeAdapterClass(Path root, String internalName) throws IOException {
        // A trivial empty class at the adapter internal name (no SAM methods needed
        // for the validator, which only checks presence).
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V22, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        writeFile(root, internalName + ".class", cw.toByteArray());
    }

    private static void writePlainClass(Path root, String internalName) throws IOException {
        writeAdapterClass(root, internalName); // same shape, different name, no adapter reference
    }

    private static byte[] classWithAdapterFieldReference(String internalName, String adapterInternal) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V22, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", null);
        // A field typed by the adapter forces a CONSTANT_Class entry in the pool.
        FieldVisitor fv = cw.visitField(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "ADAPTER_REF",
            Type.getObjectType(adapterInternal).getDescriptor(),
            null, null);
        fv.visitEnd();
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void writeFile(Path root, String relative, byte[] bytes) throws IOException {
        File out = root.resolve(relative.replace('/', File.separatorChar)).toFile();
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(bytes);
        }
    }

    private static Log silentLog() {
        return new Log() {
            public boolean isDebugEnabled() { return false; }
            public void debug(CharSequence c) {}
            public void debug(CharSequence c, Throwable e) {}
            public void debug(Throwable e) {}
            public boolean isInfoEnabled() { return false; }
            public void info(CharSequence c) {}
            public void info(CharSequence c, Throwable e) {}
            public void info(Throwable e) {}
            public boolean isWarnEnabled() { return true; }
            public void warn(CharSequence c) {}
            public void warn(CharSequence c, Throwable e) {}
            public void warn(Throwable e) {}
            public boolean isErrorEnabled() { return true; }
            public void error(CharSequence c) {}
            public void error(CharSequence c, Throwable e) {}
            public void error(Throwable e) {}
        };
    }
}
