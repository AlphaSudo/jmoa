package com.yourorg.jmoa.plugin.reducer;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReducerLayoutControlTest {

    @TempDir
    Path tempDir;

    @Test
    void repackOnlyRewritesContainerWithoutChangingAnyEntryBytes() throws Exception {
        Path input = tempDir.resolve("repack-input");
        Path output = tempDir.resolve("repack-output");
        Files.createDirectories(input);
        Path jar = input.resolve("fixture.jar");
        writeFixtureJar(jar);

        ReducerReport report = new JarReducer(config(input, output, "repack-only", "current", false)).reduce();

        assertEquals("REPACKED_ONLY", report.artifacts().get(0).status());
        assertEquals(0, report.artifacts().get(0).reducedClassCount());
        assertEquals("repack-only", report.operation());
        Path repacked = output.resolve("fixture.jar");
        assertNotEquals(JarSafetyInspector.sha256(jar.toFile()), JarSafetyInspector.sha256(repacked.toFile()));
        assertEntryBytesEqual(jar, repacked);
    }

    @Test
    void stableRawCopyPreservesUnchangedCompressedPayloadAndEntryMetadata() throws Exception {
        Path input = tempDir.resolve("stable-input");
        Path output = tempDir.resolve("stable-output");
        Files.createDirectories(input);
        Path jar = input.resolve("fixture.jar");
        writeFixtureJar(jar);

        ReducerReport report = new JarReducer(config(input, output, "strip", "stable-raw-copy", true)).reduce();

        assertEquals("REDUCED_STABLE_RAW_COPY", report.artifacts().get(0).status());
        assertEquals(1, report.artifacts().get(0).reducedClassCount());
        assertEquals(1, report.rawClassAudits().size());
        assertTrue(report.rawClassAudits().get(0).componentDigestsPreserved());
        assertEquals("stable-raw-copy", report.jarLayout());
        assertStableUnchangedEntry(jar, output.resolve("fixture.jar"), "data/payload.txt");
    }

    private static ReducerConfig config(
        Path input,
        Path output,
        String operation,
        String layout,
        boolean strip
    ) {
        return new ReducerConfig(
            false, true, "release-low-footprint", input.toFile(), output.toFile(),
            strip, strip, false, false, false, false, false, false,
            "raw", false, null, "report-only", "", "", operation, layout
        );
    }

    private static void assertEntryBytesEqual(Path expected, Path actual) throws Exception {
        try (JarFile left = new JarFile(expected.toFile()); JarFile right = new JarFile(actual.toFile())) {
            Enumeration<JarEntry> entries = left.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                JarEntry other = right.getJarEntry(entry.getName());
                assertTrue(other != null, entry.getName());
                try (var leftIn = left.getInputStream(entry); var rightIn = right.getInputStream(other)) {
                    assertArrayEquals(leftIn.readAllBytes(), rightIn.readAllBytes(), entry.getName());
                }
            }
        }
    }

    private static void assertStableUnchangedEntry(Path expected, Path actual, String name) throws Exception {
        try (ZipFile left = ZipFile.builder().setPath(expected).get();
             ZipFile right = ZipFile.builder().setPath(actual).get()) {
            List<String> leftOrder = names(left.getEntries());
            List<String> rightOrder = names(right.getEntries());
            assertEquals(leftOrder, rightOrder);
            ZipArchiveEntry leftEntry = left.getEntry(name);
            ZipArchiveEntry rightEntry = right.getEntry(name);
            assertEquals(leftEntry.getMethod(), rightEntry.getMethod());
            assertEquals(leftEntry.getTime(), rightEntry.getTime());
            assertEquals(leftEntry.getComment(), rightEntry.getComment());
            assertArrayEquals(leftEntry.getExtra(), rightEntry.getExtra());
            try (var leftRaw = left.getRawInputStream(leftEntry); var rightRaw = right.getRawInputStream(rightEntry)) {
                assertArrayEquals(leftRaw.readAllBytes(), rightRaw.readAllBytes());
            }
        }
    }

    private static List<String> names(Enumeration<ZipArchiveEntry> entries) {
        List<String> names = new ArrayList<>();
        while (entries.hasMoreElements()) names.add(entries.nextElement().getName());
        return names;
    }

    private static void writeFixtureJar(Path path) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path))) {
            JarEntry clazz = new JarEntry("com/example/Fixture.class");
            clazz.setTime(1_700_000_000_000L);
            out.putNextEntry(clazz);
            out.write(debugFixture());
            out.closeEntry();

            JarEntry resource = new JarEntry("data/payload.txt");
            resource.setTime(1_700_000_002_000L);
            resource.setComment("stable-resource");
            resource.setExtra(new byte[] {(byte) 0xca, (byte) 0xfe, 2, 0, 7, 8});
            out.putNextEntry(resource);
            out.write("payload-payload-payload".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    private static byte[] debugFixture() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Fixture", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "(I)I", null, null);
        method.visitCode();
        Label start = new Label();
        Label end = new Label();
        method.visitLabel(start);
        method.visitLineNumber(12, start);
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IADD);
        method.visitLabel(end);
        method.visitInsn(Opcodes.IRETURN);
        method.visitLocalVariable("input", "I", null, start, end, 0);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
