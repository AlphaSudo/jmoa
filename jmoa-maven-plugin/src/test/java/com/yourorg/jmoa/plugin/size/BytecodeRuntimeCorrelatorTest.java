package com.yourorg.jmoa.plugin.size;

import com.yourorg.jmoa.plugin.ClassRootDescriptor;
import com.yourorg.jmoa.plugin.ClassRootKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BytecodeRuntimeCorrelatorTest {

    @TempDir
    Path tempDir;

    @Test
    void correlatesLoadedClassesAndHistogramSurvivors() throws Exception {
        Path classes = tempDir.resolve("classes");
        writeClass(classes, "com/example/Loaded", classWithMethod("com/example/Loaded", 12_000));
        writeClass(classes, "com/example/StaticOnly", classWithMethod("com/example/StaticOnly", 50_000));

        ClassfileSizeProfile profile = new ClassfileSizeScanner(BytecodeSizeConfig.defaults()).scan(
            List.of(new ClassRootDescriptor(classes.toFile(), true, ClassRootKind.PROJECT_OUTPUT)),
            List.of()
        );
        File classLoadLog = tempDir.resolve("classload.log").toFile();
        Files.writeString(classLoadLog.toPath(),
            "[0.100s][info][class,load] com.example.Loaded source: file:/app/classes/\n");
        File histogram = tempDir.resolve("histogram.txt").toFile();
        Files.writeString(histogram.toPath(),
            "   1:             3            192  com.example.Loaded\n"
                + "Total          3            192\n");

        BytecodeRuntimeCorrelationReport report = new BytecodeRuntimeCorrelator()
            .correlate(profile, classLoadLog, histogram);

        assertEquals(2, report.totalProfileClasses());
        assertEquals(1, report.totalRuntimeLoadedClasses());
        assertEquals(1, report.profileClassesObservedLoaded());
        assertEquals(1, report.profileClassesWithHistogramInstances());
        assertTrue(report.classes().stream()
            .anyMatch(record -> record.className().equals("com.example.Loaded")
                && record.category() == RuntimeCorrelationCategory.WORKLOAD_SURVIVOR
                && record.runtimeLoaded()
                && record.histogramBytes() == 192));
        assertTrue(report.classes().stream()
            .anyMatch(record -> record.className().equals("com.example.StaticOnly")
                && record.category() == RuntimeCorrelationCategory.STATIC_ONLY_RISK
                && !record.runtimeLoaded()));
    }

    @Test
    void writesRuntimeCorrelationReports() throws Exception {
        Path classes = tempDir.resolve("classes");
        writeClass(classes, "com/example/Loaded", classWithMethod("com/example/Loaded", 9_000));
        ClassfileSizeProfile profile = new ClassfileSizeScanner(BytecodeSizeConfig.defaults()).scan(
            List.of(new ClassRootDescriptor(classes.toFile(), true, ClassRootKind.PROJECT_OUTPUT)),
            List.of()
        );
        File classLoadLog = tempDir.resolve("classload.log").toFile();
        Files.writeString(classLoadLog.toPath(),
            "[0.100s][info][class,load] com.example.Loaded source: file:/app/classes/\n");
        BytecodeRuntimeCorrelationReport report = new BytecodeRuntimeCorrelator()
            .correlate(profile, classLoadLog, null);
        File reports = tempDir.resolve("reports").toFile();

        new BytecodeRuntimeCorrelationReportWriter().write(reports, report);

        assertTrue(new File(reports, "bytecode-runtime-correlation.json").isFile());
        assertTrue(new File(reports, "bytecode-runtime-correlation.md").isFile());
        assertTrue(new File(reports, "bytecode-runtime-correlation-top-loaded.json").isFile());
        assertTrue(new File(reports, "bytecode-runtime-correlation-near64k.json").isFile());
    }

    private static byte[] classWithMethod(String internalName, int nopCount) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "big", "()V", null, null);
        method.visitCode();
        for (int i = 0; i < nopCount; i++) {
            method.visitInsn(Opcodes.NOP);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeClass(Path root, String internalName, byte[] bytes) throws Exception {
        Path target = root.resolve(internalName + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}

